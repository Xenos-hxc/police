package com.railway.security.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.railway.security")
// 架构测试把层次约束变成可执行规则；包名本身不能防止越层依赖。技术难点还应结合慢查询记录和 AI 回归案例解释。
class ArchitectureRulesTest {
    @ArchTest
    static final ArchRule controllers_must_not_access_mappers =
            noClasses()
                    .that()
                    .areAnnotatedWith(RestController.class)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..persistence.mapper..");

    @ArchTest
    static final ArchRule controller_methods_must_not_manage_transactions =
            methods()
                    .that()
                    .areDeclaredInClassesThat()
                    .areAnnotatedWith(RestController.class)
                    .should()
                    .notBeAnnotatedWith(Transactional.class);

    @ArchTest
    static final ArchRule persistence_must_not_depend_on_feature_modules =
            noClasses()
                    .that()
                    .resideInAPackage("..persistence..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..archive..",
                            "..auth..",
                            "..file..",
                            "..inspection..",
                            "..rectification..",
                            "..statistics..",
                            "..system..");
}
