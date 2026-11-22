# Relation Graph JPA

[![Java](https://shields.io)](https://openjdk.org)
[![Spring Boot](https://shields.io)](https://spring.io)
[![Hibernate](https://shields.io)](https://hibernate.org)
[![License](https://shields.io)](https://opensource.org)

> **[Русская версия документации (README_RU.md)](README_RU.md)**

**Relation Graph JPA** is a lightweight, zero-magic addon for **Spring Data JPA** that brings composable relational graph algebra (`chain`, `anyOf`, `or`, `reverse`) to your JPA queries.

Stop copy-pasting 50-line Criteria API `Subquery` trees and fragile `LEFT JOIN` chains for permission hierarchies, organizational structures, and multi-hop entity relationships. Define each relationship once as a reusable, type-safe Java class, compose them like Lego blocks, and plug them directly into any standard `JpaSpecificationExecutor`.

---

## Why Relation Graph JPA?

### Before (Raw JPA Criteria API)
Finding all reports an employee is subscribed to **directly OR through their department** requires verbose, error-prone Criteria boilerplate that is impossible to reuse or reverse:

```java
Specification<Report> spec = (root, query, cb) -> {
    Subquery<Integer> directSub = query.subquery(Integer.class);
    Root<ReportSub> subRoot = directSub.from(ReportSub.class); // Requires an @Entity for join table!
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

### After (With Relation Graph JPA)
Define the graph once, reuse it everywhere, and reverse it in a single method call:

```java
// 1. Compose the graph once: Direct subscriber OR via Department
public class ReportAllSubscribers extends CompositeRelation<Report, Employee> {
    public ReportAllSubscribers() {
        super(anyOf(
            new ReportDirectSubscribers(),
            new ReportSubscribedDepartments().chain(new DepartmentToEmployee())
        ));
    }
}

// 2. Query in 1 line using your standard Spring Data repository:
List<Report> reports = reportRepository.findAll(
    RelationSpec.of(new ReportAllSubscribers()).whereTargetId(42L)
);

// 3. Reverse the entire multi-branch graph automatically!
List<Employee> subscribers = employeeRepository.findAll(
    RelationSpec.of(new ReportAllSubscribers().reverse()).whereTargetId(10L)
);
```

---

## Key Features

* **Zero Entity Pollution:** Works with plain `@Entity` classes using scalar foreign keys (e.g., `Long departmentId`) — no `@OneToMany` or `@ManyToMany` mappings required.
* **No `@Entity` Needed for Join Tables:** `MANY_TO_MANY` intermediate tables work purely by table name via an auto-registered Hibernate 6 `FunctionContributor`.
* **Relational Algebra Composition:**
  * `.chain(next)` — type-safe multi-hop traversal (`A -> B -> C -> D...`) of unlimited depth.
  * `anyOf(...)` / `.or(...)` — parallel `OR` branches across different paths.
  * `.reverse()` — automatically inverts any direct or composite graph (`S -> T` becomes `T -> S`).
* **100% Spring Data Compatible:** Generates correlated `EXISTS` subqueries, preventing row duplication and preserving accurate `COUNT(*)` queries for `Pageable` pagination and `Sort`.
* **High Performance:** Reflection metadata is cached in a thread-safe `ConcurrentHashMap`.

---

## Installation

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

*(Note: If pulling via [JitPack](https://jitpack.io), use `com.github.aliamanticarpa:relation-graph-jpa:0.1.0`)*

---

## Quick Start

### 1. Your Standard JPA Entities & Repositories
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
    private Long departmentId; // Plain FK column
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

### 2. Define Direct Relations
Annotate subclasses of `AbstractDirectRelation<S, T>` with `@Relation`:

```java
import io.github.aliamanticarpa.relationgraph.core.annotations.Relation;
import io.github.aliamanticarpa.relationgraph.core.relations.AbstractDirectRelation;
import io.github.aliamanticarpa.relationgraph.core.relations.RelationType;

// Direct 1:N via FK (automatically maps department.id = employee.department_id)
@Relation(type = RelationType.ONE_TO_MANY)
public class DepartmentToEmployee extends AbstractDirectRelation<Department, Employee> {}

// M:N via intermediate table (no @Entity needed for report_employee_subs!)
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

### 3. Compose Complex Graph Relations
Combine direct relations into reusable graphs using `CompositeRelation<S, T>`:

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

### 4. Query with `RelationSpec`
`RelationSpec` produces a standard Spring Data `Specification<S>`:

```java
import io.github.aliamanticarpa.relationgraph.jpa.RelationSpec;

// Filter by target primary key ("id")
Specification<Report> byEmployeeId = RelationSpec.of(new ReportAllSubscribers())
        .whereTargetId(42L);

// Filter by target field equality
Specification<Report> byEmployeeName = RelationSpec.of(new ReportAllSubscribers())
        .whereTargetEq("fullName", "Ivan Petrov");

// Filter by IN collection
Specification<Report> byEmployeeIds = RelationSpec.of(new ReportAllSubscribers())
        .whereTargetIn("id", List.of(42L, 99L));

// Custom Criteria API predicate on the target entity
Specification<Report> customFilter = RelationSpec.of(new ReportAllSubscribers())
        .whereTarget((emp, query, cb) -> cb.like(emp.get("fullName"), "Ivan%"));

// Check existence without filtering the target
Specification<Report> hasAnySubscriber = RelationSpec.of(new ReportAllSubscribers())
        .exists();

// Combine with standard Specifications, Sort, and Pageable
Page<Report> activeReports = reportRepository.findAll(
    byEmployeeId.and((root, q, cb) -> cb.equal(root.get("status"), "ACTIVE")),
    PageRequest.of(0, 20, Sort.by("id").ascending())
);
```

---

## `@Relation` Annotation Reference

| Attribute | Default | Description |
| :--- | :--- | :--- |
| `type` | **Required** | `ONE_TO_ONE`, `ONE_TO_MANY`, `MANY_TO_ONE`, or `MANY_TO_MANY`. |
| `joinTable` | `false` | Forces join-table mode for `ONE_TO_MANY` / `ONE_TO_ONE` (always `true` for `MANY_TO_MANY`). |
| `name` | `snake_case(ClassName)` | Name of the intermediate join table (when `joinTable = true` or `MANY_TO_MANY`). |
| `sourceColumn` | Auto-resolved | FK column in join table (if `joinTable = true`) or join column in source entity `S`. |
| `targetColumn` | Auto-resolved | FK column in join table (if `joinTable = true`) or join column in target entity `T`. |
| `sourcePk` | `"id"` | Primary key column name in source entity `S` (used when `joinTable = true`). |
| `targetPk` | `"id"` | Primary key column name in target entity `T` (used when `joinTable = true`). |

---

## Compatibility

| Component | Supported Versions | Notes |
| :--- | :--- | :--- |
| **Java** | **17+** (17, 21, 23+) | Compiled with `--release 17` |
| **Spring Boot / Data JPA** | **3.0+** | Uses `jakarta.persistence` |
| **Hibernate ORM** | **6.0+** | Auto-registers `FunctionContributor` via SPI |
| **Databases** | PostgreSQL, H2, MySQL, Oracle, MS SQL | Dialect-aware identifier quoting |

---

## License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.
