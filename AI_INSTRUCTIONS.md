# AI Assistant Instructions: `relation-graph-jpa`

Use this reference when generating Java code with the `io.github.aliamanticarpa:relation-graph-jpa` library (Spring Data JPA + Hibernate 6).

---

## 1. Core Architecture & Packages

* **Core Package:** `io.github.aliamanticarpa.relationgraph.core.*`
    * `@Relation` (`core.annotations.Relation`)
    * `RelationType` (`core.relations.RelationType`) — `ONE_TO_ONE`, `ONE_TO_MANY`, `MANY_TO_ONE`, `MANY_TO_MANY`
    * `TableRelation<S, T>` (`core.relations.TableRelation`)
    * `AbstractDirectRelation<S, T>` (`core.relations.AbstractDirectRelation`)
    * `CompositeRelation<S, T>` (`core.relations.CompositeRelation`)
* **JPA Adapter Package:** `io.github.aliamanticarpa.relationgraph.jpa.*`
    * `RelationSpec<S, T>` (`jpa.RelationSpec`)
    * `TargetPredicate<T>` (`jpa.TargetPredicate`)

---

## 2. Strict Rules for Code Generation

1. **Instantiate Relations Directly:** Always instantiate relation classes using `new MyRelation()`. Do NOT pass `.class` literals to `RelationSpec.of(...)` or `.chain(...)`.
2. **No `@Entity` for Join Tables:** Never create JPA `@Entity` classes for `MANY_TO_MANY` intermediate tables. Define them purely via `@Relation(type = RelationType.MANY_TO_MANY, name = "table_name", sourceColumn = "...", targetColumn = "...")`.
3. **Scalar Foreign Keys Supported:** Entities do NOT need `@OneToMany`, `@ManyToOne`, or `@ManyToMany` fields. Plain scalar ID columns (e.g., `private Long departmentId;`) are resolved automatically via `@Column(name = "...")` or `snake_case` field name matching.
4. **Repository Requirement:** Target repositories must extend `JpaSpecificationExecutor<S>`.

---

## 3. Code Templates

### A. Direct Relations (`AbstractDirectRelation<S, T>`)

```java
// 1. Direct ONE_TO_MANY (FK is on target table T: department.id = employee.department_id)
@Relation(type = RelationType.ONE_TO_MANY)
public class DepartmentToEmployee extends AbstractDirectRelation<Department, Employee> {}

// 2. Direct MANY_TO_ONE (FK is on source table S: employee.department_id = department.id)
@Relation(type = RelationType.MANY_TO_ONE)
public class EmployeeToDepartment extends AbstractDirectRelation<Employee, Department> {}

// 3. Direct ONE_TO_ONE (FK is on target table T: employee.id = employee_profile.employee_id)
@Relation(type = RelationType.ONE_TO_ONE)
public class EmployeeToProfile extends AbstractDirectRelation<Employee, EmployeeProfile> {}

// 4. Custom PK and FK column names
@Relation(type = RelationType.ONE_TO_MANY, sourcePk = "company_code", targetColumn = "comp_code")
public class CompanyToDepartment extends AbstractDirectRelation<Company, Department> {}

// 5. MANY_TO_MANY via intermediate table (no @Entity needed)
@Relation(
    type = RelationType.MANY_TO_MANY,
    name = "report_employee_subs",
    sourceColumn = "report_id",
    targetColumn = "employee_id"
)
public class ReportDirectSubscribers extends AbstractDirectRelation<Report, Employee> {}

// 6. ONE_TO_MANY via explicit join table
@Relation(
    type = RelationType.ONE_TO_MANY,
    joinTable = true,
    name = "department_heads",
    sourceColumn = "department_id",
    targetColumn = "head_employee_id"
)
public class DepartmentToHead extends AbstractDirectRelation<Department, Employee> {}
```

### B. Composing Graphs (`CompositeRelation<S, T>` & Fluent Methods)

Any `TableRelation<S, T>` supports `.chain(next)`, `.or(alternative)`, and `.reverse()`:

```java
public class ReportAllSubscribers extends CompositeRelation<Report, Employee> {
    public ReportAllSubscribers() {
        super(anyOf(
            new ReportDirectSubscribers(),
            new ReportSubscribedDepartments().chain(new DepartmentToEmployee())
        ));
    }
}

// Unlimited multi-hop chain on the fly:
TableRelation<Company, Report> companyToReports = new CompanyToDepartment()
        .chain(new DepartmentToEmployee())
        .chain(new ReportAllSubscribers().reverse());
```

### C. Querying via `RelationSpec<S, T>`

```java
// 1. Filter by target primary key ("id")
Specification<Report> s1 = RelationSpec.of(new ReportAllSubscribers()).whereTargetId(42L);

// 2. Filter by target field equality
Specification<Report> s2 = RelationSpec.of(new ReportAllSubscribers()).whereTargetEq("email", "user@example.com");

// 3. Filter by target field IN collection
Specification<Report> s3 = RelationSpec.of(new ReportAllSubscribers()).whereTargetIn("id", List.of(1L, 2L));

// 4. Custom Criteria API predicate on target entity
Specification<Report> s4 = RelationSpec.of(new ReportAllSubscribers())
        .whereTarget((target, query, cb) -> cb.equal(target.get("active"), true));

// 5. Existence check without target filter
Specification<Report> s5 = RelationSpec.of(new ReportAllSubscribers()).exists();

// 6. Combine with standard Specifications and Pageable
Page<Report> page = reportRepository.findAll(
        s1.and((root, query, cb) -> cb.equal(root.get("status"), "PUBLISHED")),
        PageRequest.of(0, 20, Sort.by("id").ascending())
);
```
