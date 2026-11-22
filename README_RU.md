# Relation Graph JPA

[![Java](https://shields.io)](https://openjdk.org)
[![Spring Boot](https://shields.io)](https://spring.io)
[![Hibernate](https://shields.io)](https://hibernate.org)
[![License](https://shields.io)](https://opensource.org)

> **[English documentation (README.md)](README.md)**

**Relation Graph JPA** — лёгкий аддон без лишней магии для **Spring Data JPA**, который добавляет композируемую алгебру графовых связей (`chain`, `anyOf`, `or`, `reverse`) прямо в ваши JPA-запросы.

Перестаньте копипастить 50-строчные деревья `Subquery` на Criteria API и хрупкие цепочки `LEFT JOIN` для матриц прав доступа, оргструктур и многошаговых связей между таблицами. Опишите каждую связь один раз в виде типобезопасного Java-класса, собирайте из них графы как конструктор Lego и передавайте в любой стандартный `JpaSpecificationExecutor`.

---

## Зачем нужен Relation Graph JPA?

### До (голый JPA Criteria API)
Чтобы найти все отчёты, на которые сотрудник подписан **лично ИЛИ через свой департамент**, приходится писать громоздкий код на Criteria API, который невозможно переиспользовать или развернуть в обратную сторону:

```java
Specification<Report> spec = (root, query, cb) -> {
    Subquery<Integer> directSub = query.subquery(Integer.class);
    Root<ReportSub> subRoot = directSub.from(ReportSub.class); // Требует отдельный @Entity для связующей таблицы!
    directSub.select(cb.literal(1)).where(
        cb.equal(subRoot.get("reportId"), root.get("id")),
        cb.equal(subRoot.get("employeeId"), employeeId)
    );

    Subquery<Integer> deptSub = query.subquery(Integer.class);
    Root<ReportDeptSub> deptSubRoot = deptSub.from(ReportDeptSub.class);
    Root<Employee> empRoot = deptSub.from(Employee.class);
    deptSub.select(cb.literal(1)).where(
        cb.equal(deptSubRoot.get("reportId"), root.get("id")),
        cb.equal(deptSubRoot.get("departmentId"), empRoot.get("departmentId")),
        cb.equal(empRoot.get("id"), employeeId)
    );

    return cb.or(cb.exists(directSub), cb.exists(deptSub));
};
```

### После (с Relation Graph JPA)
Опишите граф один раз, переиспользуйте в любом сервисе и разворачивайте в обратную сторону одним методом:

```java
// 1. Описываем композитный граф один раз: личный подписчик ИЛИ через отдел
public class ReportAllSubscribers extends CompositeRelation<Report, Employee> {
    public ReportAllSubscribers() {
        super(anyOf(
            new ReportDirectSubscribers(),
            new ReportSubscribedDepartments().chain(new DepartmentToEmployee())
        ));
    }
}

// 2. Делаем выборку в 1 строчку через стандартный Spring Data репозиторий:
List<Report> reports = reportRepository.findAll(
    RelationSpec.of(new ReportAllSubscribers()).whereTargetId(42L)
);

// 3. Разворачиваем весь многоветочный граф в обратную сторону автоматически!
List<Employee> subscribers = employeeRepository.findAll(
    RelationSpec.of(new ReportAllSubscribers().reverse()).whereTargetId(10L)
);
```

---

## Ключевые возможности

* **Никакого загрязнения `@Entity`:** работает с обычными сущностями, где внешние ключи хранятся простыми полями (например, `Long departmentId`) — аннотации `@OneToMany` и `@ManyToMany` не требуются.
* **Связующие таблицы без `@Entity`:** промежуточные таблицы `MANY_TO_MANY` работают исключительно по имени таблицы в БД благодаря авторегистрируемому `FunctionContributor` для Hibernate 6.
* **Алгебра композиции связей:**
    * `.chain(next)` — типобезопасная цепочка переходов (`A -> B -> C -> D...`) неограниченной длины.
    * `anyOf(...)` / `.or(...)` — параллельные ветки `OR` по разным путям.
    * `.reverse()` — автоматический разворот любой прямой или композитной связи (`S -> T` превращается в `T -> S`).
* **100% совместимость со Spring Data:** генерирует коррелированные подзапросы `EXISTS`, что исключает дублирование строк и сохраняет точный `COUNT(*)` для пагинации `Pageable` и сортировки `Sort`.
* **Высокая производительность:** метаданные рефлексии кэшируются в потокобезопасном `ConcurrentHashMap`.

---

## Установка

### Maven
```xml
<dependency>
    <groupId>io.github.aliamanticarpa</groupId>
    <artifactId>relation-graph-jpa</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle
```groovy
implementation 'io.github.aliamanticarpa:relation-graph-jpa:0.1.0'
```

*(Примечание: при подключении через [JitPack](https://jitpack.io) используйте координаты `com.github.aliamanticarpa:relation-graph-jpa:0.1.0`)*

---

## Быстрый старт

### 1. Обычные JPA-сущности и репозитории
```java
@Entity
@Table(name = "departments")
public class Department {
    @Id private Long id;
    private String title;
}

@Entity
@Table(name = "employees")
public class Employee {
    @Id private Long id;
    private String fullName;
    private Long departmentId; // Обычный внешний ключ
}

@Entity
@Table(name = "reports")
public class Report {
    @Id private Long id;
    private String title;
    private String status;
}

public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {}
```

### 2. Определение прямых связей
Унаследуйте класс от `AbstractDirectRelation<S, T>` и добавьте аннотацию `@Relation`:

```java
import io.github.aliamanticarpa.relationgraph.core.annotations.Relation;
import io.github.aliamanticarpa.relationgraph.core.relations.AbstractDirectRelation;
import io.github.aliamanticarpa.relationgraph.core.relations.RelationType;

// Прямая связь 1:N по внешнему ключу (автоматически свяжет department.id = employee.department_id)
@Relation(type = RelationType.ONE_TO_MANY)
public class DepartmentToEmployee extends AbstractDirectRelation<Department, Employee> {}

// Связь M:N через промежуточную таблицу (создавать @Entity для report_employee_subs не нужно!)
@Relation(
    type = RelationType.MANY_TO_MANY,
    name = "report_employee_subs",
    sourceColumn = "report_id",
    targetColumn = "employee_id"
)
public class ReportDirectSubscribers extends AbstractDirectRelation<Report, Employee> {}

@Relation(
    type = RelationType.MANY_TO_MANY,
    name = "report_department_subs",
    sourceColumn = "report_id",
    targetColumn = "department_id"
)
public class ReportSubscribedDepartments extends AbstractDirectRelation<Report, Department> {}
```

### 3. Композиция сложных графов
Объединяйте прямые связи в переиспользуемые графы с помощью `CompositeRelation<S, T>`:

```java
import io.github.aliamanticarpa.relationgraph.core.relations.CompositeRelation;

public class ReportAllSubscribers extends CompositeRelation<Report, Employee> {
    public ReportAllSubscribers() {
        super(anyOf(
            new ReportDirectSubscribers(),
            new ReportSubscribedDepartments().chain(new DepartmentToEmployee())
        ));
    }
}
```

### 4. Построение запросов через `RelationSpec`
`RelationSpec` возвращает стандартную Spring Data `Specification<S>`:

```java
import io.github.aliamanticarpa.relationgraph.jpa.RelationSpec;

// Фильтр по первичному ключу ("id") целевой сущности
Specification<Report> byEmployeeId = RelationSpec.of(new ReportAllSubscribers())
        .whereTargetId(42L);

// Фильтр по равенству любого поля целевой сущности
Specification<Report> byEmployeeName = RelationSpec.of(new ReportAllSubscribers())
        .whereTargetEq("fullName", "Ivan Petrov");

// Фильтр по списку значений IN
Specification<Report> byEmployeeIds = RelationSpec.of(new ReportAllSubscribers())
        .whereTargetIn("id", List.of(42L, 99L));

// Произвольный предикат Criteria API на целевую сущность
Specification<Report> customFilter = RelationSpec.of(new ReportAllSubscribers())
        .whereTarget((emp, query, cb) -> cb.like(emp.get("fullName"), "Ivan%"));

// Проверка наличия хотя бы одной связанной записи без фильтрации цели
Specification<Report> hasAnySubscriber = RelationSpec.of(new ReportAllSubscribers())
        .exists();

// Комбинирование с обычными Specification, сортировкой и пагинацией Pageable
Page<Report> activeReports = reportRepository.findAll(
    byEmployeeId.and((root, q, cb) -> cb.equal(root.get("status"), "ACTIVE")),
    PageRequest.of(0, 20, Sort.by("id").ascending())
);
```

---

## Справочник аннотации `@Relation`

| Параметр | По умолчанию | Описание |
| :--- | :--- | :--- |
| `type` | **Обязателен** | `ONE_TO_ONE`, `ONE_TO_MANY`, `MANY_TO_ONE` или `MANY_TO_MANY`. |
| `joinTable` | `false` | Принудительно включает режим промежуточной таблицы для `ONE_TO_MANY` / `ONE_TO_ONE` (для `MANY_TO_MANY` всегда `true`). |
| `name` | `snake_case(ClassName)` | Имя промежуточной таблицы в БД (при `joinTable = true` или `MANY_TO_MANY`). |
| `sourceColumn` | Авторасчёт | Колонка внешнего ключа в связующей таблице (если `joinTable = true`) либо колонка в таблице `S`. |
| `targetColumn` | Авторасчёт | Колонка внешнего ключа в связующей таблице (если `joinTable = true`) либо колонка в таблице `T`. |
| `sourcePk` | `"id"` | Имя первичного ключа в таблице `S` (используется при `joinTable = true`). |
| `targetPk` | `"id"` | Имя первичного ключа в таблице `T` (используется при `joinTable = true`). |

---

## Совместимость

| Компонент | Поддерживаемые версии | Примечание |
| :--- | :--- | :--- |
| **Java** | **17+** (17, 21, 23+) | Скомпилировано с `--release 17` |
| **Spring Boot / Data JPA** | **3.0+** | Использует пакет `jakarta.persistence` |
| **Hibernate ORM** | **6.0+** | Авторегистрация `FunctionContributor` через SPI |
| **СУБД** | PostgreSQL, H2, MySQL, Oracle, MS SQL | Экранирование идентификаторов под диалект БД |

---

## Лицензия

Проект распространяется под лицензией **Apache License 2.0** — подробности в файле [LICENSE](LICENSE).
