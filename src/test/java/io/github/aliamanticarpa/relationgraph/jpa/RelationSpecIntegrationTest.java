package io.github.aliamanticarpa.relationgraph.jpa;

import io.github.aliamanticarpa.relationgraph.core.annotations.Relation;
import io.github.aliamanticarpa.relationgraph.core.relations.AbstractDirectRelation;
import io.github.aliamanticarpa.relationgraph.core.relations.CompositeRelation;
import io.github.aliamanticarpa.relationgraph.core.relations.RelationType;
import jakarta.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:pg_compat_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.format_sql=true"
})
class RelationSpecIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = RelationSpecIntegrationTest.class)
    @EnableJpaRepositories(
            basePackageClasses = RelationSpecIntegrationTest.class,
            considerNestedRepositories = true
    )
    static class TestApp {}

    // --- 1. Entities ---

    @Entity
    @Table(name = "companies")
    static class Company {
        @Id
        @Column(name = "company_code")
        String code;
        String name;

        Company() {}
        Company(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    @Entity
    @Table(name = "departments")
    static class Department {
        @Id
        Long id;
        String title;

        @Column(name = "comp_code")
        String companyCode;

        Department() {}
        Department(Long id, String title, String companyCode) {
            this.id = id;
            this.title = title;
            this.companyCode = companyCode;
        }
    }

    @Entity
    @Table(name = "employees")
    static class Employee {
        @Id
        Long id;
        String fullName;
        Long departmentId;

        Employee() {}
        Employee(Long id, String fullName, Long departmentId) {
            this.id = id;
            this.fullName = fullName;
            this.departmentId = departmentId;
        }
    }

    @Entity
    @Table(name = "employee_profiles")
    static class EmployeeProfile {
        @Id
        Long id;
        Long employeeId;
        String telegram;

        EmployeeProfile() {}
        EmployeeProfile(Long id, Long employeeId, String telegram) {
            this.id = id;
            this.employeeId = employeeId;
            this.telegram = telegram;
        }
    }

    @Entity
    @Table(name = "reports")
    static class Report {
        @Id
        Long id;
        String title;
        String status;

        Report() {}
        Report(Long id, String title, String status) {
            this.id = id;
            this.title = title;
            this.status = status;
        }
    }

    // --- 2. Graph Relations ---

    @Relation(type = RelationType.ONE_TO_MANY, sourcePk = "company_code", targetColumn = "comp_code")
    public static class CompanyToDepartment extends AbstractDirectRelation<Company, Department> {}

    @Relation(type = RelationType.ONE_TO_MANY)
    public static class DepartmentToEmployee extends AbstractDirectRelation<Department, Employee> {}

    @Relation(type = RelationType.MANY_TO_ONE)
    public static class EmployeeToDepartment extends AbstractDirectRelation<Employee, Department> {}

    @Relation(type = RelationType.ONE_TO_ONE)
    public static class EmployeeToProfile extends AbstractDirectRelation<Employee, EmployeeProfile> {}

    // ONE_TO_MANY with explicit joinTable = true
    @Relation(type = RelationType.ONE_TO_MANY, joinTable = true, name = "department_heads",
            sourceColumn = "department_id", targetColumn = "head_employee_id")
    public static class DepartmentToHead extends AbstractDirectRelation<Department, Employee> {}

    @Relation(type = RelationType.MANY_TO_MANY, name = "report_employee_subs",
            sourceColumn = "report_id", targetColumn = "employee_id")
    public static class ReportDirectSubscribers extends AbstractDirectRelation<Report, Employee> {}

    @Relation(type = RelationType.MANY_TO_MANY, name = "report_department_subs",
            sourceColumn = "report_id", targetColumn = "department_id")
    public static class ReportSubscribedDepartments extends AbstractDirectRelation<Report, Department> {}

    // Reserved SQL keywords test ("group" table, "order" column)
    @Relation(type = RelationType.MANY_TO_MANY, name = "group",
            sourceColumn = "report_id", targetColumn = "order")
    public static class ReservedWordRelation extends AbstractDirectRelation<Report, Employee> {}

    public static class ReportAllSubscribers extends CompositeRelation<Report, Employee> {
        public ReportAllSubscribers() {
            super(anyOf(
                    new ReportDirectSubscribers(),
                    new ReportSubscribedDepartments().chain(new DepartmentToEmployee())
            ));
        }
    }

    // --- 3. Repositories ---

    interface CompanyRepo extends JpaRepository<Company, String>, JpaSpecificationExecutor<Company> {}
    interface DepartmentRepo extends JpaRepository<Department, Long>, JpaSpecificationExecutor<Department> {}
    interface EmployeeRepo extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {}
    interface ProfileRepo extends JpaRepository<EmployeeProfile, Long>, JpaSpecificationExecutor<EmployeeProfile> {}
    interface ReportRepo extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {}

    @Autowired CompanyRepo companyRepo;
    @Autowired DepartmentRepo departmentRepo;
    @Autowired EmployeeRepo employeeRepo;
    @Autowired ProfileRepo profileRepo;
    @Autowired ReportRepo reportRepo;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS report_employee_subs (report_id BIGINT, employee_id BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS report_department_subs (report_id BIGINT, department_id BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS department_heads (department_id BIGINT, head_employee_id BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"group\" (report_id BIGINT, \"order\" BIGINT)");

        jdbc.execute("DELETE FROM report_employee_subs");
        jdbc.execute("DELETE FROM report_department_subs");
        jdbc.execute("DELETE FROM department_heads");
        jdbc.execute("DELETE FROM \"group\"");

        companyRepo.save(new Company("COMP_1", "Alpha Bank"));
        companyRepo.save(new Company("COMP_2", "Beta Finance"));

        departmentRepo.save(new Department(10L, "IT", "COMP_1"));
        departmentRepo.save(new Department(20L, "HR", "COMP_2"));

        employeeRepo.save(new Employee(42L, "Ivan Petrov", 10L));
        employeeRepo.save(new Employee(99L, "Anna Sidorova", 20L));

        profileRepo.save(new EmployeeProfile(100L, 42L, "@ivan_dev"));

        reportRepo.save(new Report(1L, "Ivan Personal Report", "ACTIVE"));
        reportRepo.save(new Report(2L, "IT Department Report", "ACTIVE"));
        reportRepo.save(new Report(3L, "Ivan Archived Report", "ARCHIVED"));
        reportRepo.save(new Report(4L, "HR Department Report", "ACTIVE"));
        reportRepo.save(new Report(5L, "Unsubscribed Report", "ACTIVE"));

        jdbc.update("INSERT INTO report_employee_subs VALUES (1, 42)");
        jdbc.update("INSERT INTO report_employee_subs VALUES (3, 42)");
        jdbc.update("INSERT INTO report_department_subs VALUES (2, 10)");
        jdbc.update("INSERT INTO report_department_subs VALUES (4, 20)");
        jdbc.update("INSERT INTO department_heads VALUES (10, 42)");
        jdbc.update("INSERT INTO \"group\" VALUES (1, 42)");
    }

    @Test
    void test1_whereTargetEq_findsDirectAndDepartmentReports() {
        Specification<Report> spec = RelationSpec.of(new ReportAllSubscribers())
                .whereTargetEq("id", 42L);

        assertThat(reportRepo.findAll(spec))
                .extracting(r -> r.title)
                .containsExactlyInAnyOrder("Ivan Personal Report", "IT Department Report", "Ivan Archived Report");
    }

    @Test
    void test2_whereTargetIn_andEmptyCollection() {
        Specification<Report> specIn = RelationSpec.of(new ReportAllSubscribers())
                .whereTargetIn("id", List.of(42L, 99L));

        assertThat(reportRepo.findAll(specIn))
                .extracting(r -> r.id)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);

        Specification<Report> emptySpec = RelationSpec.of(new ReportAllSubscribers())
                .whereTargetIn("id", List.of());

        assertThat(reportRepo.findAll(emptySpec)).isEmpty();
    }

    @Test
    void test3_reverseGraph_findsEmployeesForReport() {
        Specification<Employee> spec = RelationSpec.of(new ReportAllSubscribers().reverse())
                .whereTargetEq("id", 2L);

        assertThat(employeeRepo.findAll(spec))
                .extracting(e -> e.fullName)
                .containsExactly("Ivan Petrov");
    }

    @Test
    void test4_manyToOneAndOneToManyWithJoinTable() {
        Specification<Employee> empSpec = RelationSpec.of(new EmployeeToDepartment())
                .whereTargetEq("title", "IT");
        assertThat(employeeRepo.findAll(empSpec)).extracting(e -> e.fullName).containsExactly("Ivan Petrov");

        Specification<Department> headDeptSpec = RelationSpec.of(new DepartmentToHead())
                .whereTargetEq("id", 42L);
        assertThat(departmentRepo.findAll(headDeptSpec)).extracting(d -> d.title).containsExactly("IT");

        Specification<Employee> deptHeadSpec = RelationSpec.of(new DepartmentToHead().reverse())
                .whereTargetEq("title", "IT");
        assertThat(employeeRepo.findAll(deptHeadSpec)).extracting(e -> e.fullName).containsExactly("Ivan Petrov");
    }

    @Test
    void test5_oneToOne_findsEmployeeByProfile() {
        Specification<Employee> spec = RelationSpec.of(new EmployeeToProfile())
                .whereTargetEq("telegram", "@ivan_dev");

        assertThat(employeeRepo.findAll(spec))
                .extracting(e -> e.fullName)
                .containsExactly("Ivan Petrov");
    }

    @Test
    void test6_customPrimaryKeyAnd3HopChain() {
        var companyToReports = new CompanyToDepartment()
                .chain(new DepartmentToEmployee())
                .chain(new ReportAllSubscribers().reverse());

        Specification<Company> spec = RelationSpec.of(companyToReports)
                .whereTargetEq("title", "HR Department Report");

        assertThat(companyRepo.findAll(spec))
                .extracting(c -> c.name)
                .containsExactly("Beta Finance");
    }

    @Test
    void test7_existsWithoutTargetFilter() {
        Specification<Report> spec = RelationSpec.of(new ReportAllSubscribers()).exists();

        assertThat(reportRepo.findAll(spec))
                .extracting(r -> r.id)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void test8_paginationSortingAndCombiningWithStandardSpecifications() {
        Specification<Report> activeStatusSpec = (root, query, cb) -> cb.equal(root.get("status"), "ACTIVE");

        Specification<Report> combinedSpec = RelationSpec.of(new ReportAllSubscribers())
                .whereTargetEq("id", 42L)
                .and(activeStatusSpec);

        Page<Report> page = reportRepo.findAll(
                combinedSpec,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "id"))
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(r -> r.title)
                .containsExactly("Ivan Personal Report");
    }

    @Test
    void test9_reservedSqlKeywordsAndPostgresMode() {
        Specification<Report> spec = RelationSpec.of(new ReservedWordRelation())
                .whereTargetEq("id", 42L);

        assertThat(reportRepo.findAll(spec))
                .extracting(r -> r.id)
                .containsExactly(1L);
    }
}
