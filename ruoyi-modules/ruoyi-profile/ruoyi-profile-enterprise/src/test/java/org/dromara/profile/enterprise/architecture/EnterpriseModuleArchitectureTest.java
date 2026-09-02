package org.dromara.profile.enterprise.architecture;

import cn.dev33.satoken.annotation.SaIgnore;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.profile.enterprise.controller.admin.EnterpriseAdminController;
import org.dromara.profile.enterprise.controller.admin.EnterpriseMaterialAdminController;
import org.dromara.profile.enterprise.controller.anonymous.EnterpriseVerificationAnonymousController;
import org.dromara.profile.enterprise.controller.self.EnterpriseApplicationController;
import org.dromara.profile.enterprise.controller.self.EnterpriseMaterialSelfController;
import org.dromara.profile.enterprise.controller.self.EnterpriseTransferController;
import org.dromara.profile.enterprise.domain.ProfileEnterpriseApplication;
import org.dromara.profile.enterprise.domain.ProfileEnterpriseBinding;
import org.dromara.profile.enterprise.domain.ProfileEnterpriseBindingEvent;
import org.dromara.profile.enterprise.domain.ProfileEnterprise;
import org.dromara.profile.enterprise.domain.ProfileEnterpriseSource;
import org.dromara.profile.enterprise.domain.ProfileEnterpriseSubmission;
import org.dromara.profile.enterprise.domain.ProfileEnterpriseTransferRecord;
import org.dromara.profile.enterprise.domain.ProfileEnterpriseVersion;
import org.dromara.profile.enterprise.domain.ProfileVerificationAttempt;
import org.dromara.profile.enterprise.mapper.EnterpriseAdminMapper;
import org.dromara.profile.enterprise.mapper.EnterpriseApplicationMapper;
import org.dromara.profile.enterprise.mapper.EnterpriseTransferMapper;
import org.dromara.profile.enterprise.mapper.EnterpriseVerificationAttemptMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class EnterpriseModuleArchitectureTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
        EnterpriseAdminController.class,
        EnterpriseMaterialAdminController.class,
        EnterpriseApplicationController.class,
        EnterpriseMaterialSelfController.class,
        EnterpriseTransferController.class,
        EnterpriseVerificationAnonymousController.class
    );
    private static final List<Class<?>> MAPPERS = List.of(
        EnterpriseAdminMapper.class,
        EnterpriseApplicationMapper.class,
        EnterpriseTransferMapper.class,
        EnterpriseVerificationAttemptMapper.class
    );

    @Test
    void serviceTreeContainsOnlyRootInterfacesAndImplDirectory() throws IOException {
        Path serviceRoot = Path.of(System.getProperty("basedir"), "src/main/java/org/dromara/profile/enterprise/service");
        try (var children = Files.list(serviceRoot)) {
            assertThat(children.filter(Files::isDirectory).filter(this::containsJavaSource)
                .map(path -> path.getFileName().toString()))
                .containsExactly("impl");
        }
        try (var files = Files.list(serviceRoot)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java")))
                .allSatisfy(path -> assertThat(Files.readString(path)).contains("public interface "));
        }
    }

    private boolean containsJavaSource(Path directory) {
        try (var files = Files.walk(directory)) {
            return files.anyMatch(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect service directory " + directory, exception);
        }
    }

    @Test
    void serviceImplementationDoesNotReintroduceDataSupportForwarders() throws IOException {
        Path serviceRoot = Path.of(System.getProperty("basedir"), "src/main/java/org/dromara/profile/enterprise/service");
        try (var files = Files.walk(serviceRoot)) {
            assertThat(files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()))
                .noneMatch(name -> name.endsWith("DataSupport.java"));
        }
    }

    @Test
    void controllersUseOnlyExplicitAccessSurfacePackages() {
        assertThat(CONTROLLERS).allSatisfy(controller -> assertThat(controller.getPackageName())
            .matches("org\\.dromara\\.profile\\.enterprise\\.controller\\.(admin|self|anonymous)"));
        CONTROLLERS.forEach(controller -> Arrays.stream(controller.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(SaIgnore.class))
            .forEach(method -> {
                assertThat(controller.getPackageName()).endsWith(".anonymous");
                assertThat(controller.getSimpleName()).contains("Anonymous");
            }));
    }

    @Test
    void mapperInterfacesContainNoSqlAnnotationsAndExtendBaseMapperPlus() {
        Set<Class<? extends Annotation>> forbidden = Set.of(Select.class, Insert.class, Update.class, Delete.class);
        MAPPERS.forEach(mapper -> {
            assertThat(BaseMapperPlus.class.isAssignableFrom(mapper)).as(mapper.getSimpleName()).isTrue();
            Arrays.stream(mapper.getDeclaredMethods()).forEach(method ->
                assertThat(Arrays.stream(method.getAnnotations())
                    .map(Annotation::annotationType)
                    .noneMatch(forbidden::contains)).as(mapper.getSimpleName() + "." + method.getName()).isTrue());
        });
    }

    @Test
    void mapperXmlNamespaceAndStatementIdsMatchInterfaces() throws Exception {
        for (Class<?> mapper : MAPPERS) {
            String resource = "mapper/enterprise/" + mapper.getSimpleName() + ".xml";
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            Document document;
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertThat(input).as(resource).isNotNull();
                document = factory.newDocumentBuilder().parse(input);
            }
            assertThat(document.getDocumentElement().getAttribute("namespace")).isEqualTo(mapper.getName());
            Set<String> ids = Arrays.stream(new String[]{"select", "insert", "update", "delete"})
                .flatMap(tag -> elements(document, tag).stream())
                .map(element -> element.getAttribute("id"))
                .collect(Collectors.toSet());
            Set<String> expected = Arrays.stream(mapper.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
            assertThat(ids).containsExactlyInAnyOrderElementsOf(expected);
            if (mapper == EnterpriseTransferMapper.class) {
                assertThat(document.getDocumentElement().getTextContent())
                    .doesNotContain("profile_person", "profile_person_binding", "sys_user");
            }
        }
    }

    @Test
    void ownerEntitiesCarryTableVersionAndLogicalDeleteMetadata() throws Exception {
        assertEntity(ProfileEnterprise.class, "profile_enterprise");
        assertEntity(ProfileEnterpriseApplication.class, "profile_enterprise_application");
        assertEntity(ProfileEnterpriseBinding.class, "profile_enterprise_binding");
        assertEntity(ProfileEnterpriseBindingEvent.class, "profile_enterprise_binding_event");
        assertEntity(ProfileEnterpriseSource.class, "profile_enterprise_source");
        assertEntity(ProfileEnterpriseSubmission.class, "profile_enterprise_submission");
        assertEntity(ProfileEnterpriseTransferRecord.class, "profile_enterprise_transfer_record");
        assertEntity(ProfileEnterpriseVersion.class, "profile_enterprise_version");
        assertEntity(ProfileVerificationAttempt.class, "profile_verification_attempt");
        assertGenerated(ProfileEnterprise.class, "activeCreditCode");
        assertGenerated(ProfileEnterpriseApplication.class, "openUserId", "openIdentityKey");
        assertGenerated(ProfileEnterpriseBinding.class, "effectiveUserId", "effectiveProfileId");
        assertGenerated(ProfileEnterpriseVersion.class, "currentProfileId");
    }

    private void assertGenerated(Class<?> entity, String... fields) throws Exception {
        for (String fieldName : fields) {
            TableField mapping = entity.getDeclaredField(fieldName).getAnnotation(TableField.class);
            assertThat(mapping).as(entity.getSimpleName() + "." + fieldName).isNotNull();
            assertThat(mapping.insertStrategy()).isEqualTo(FieldStrategy.NEVER);
            assertThat(mapping.updateStrategy()).isEqualTo(FieldStrategy.NEVER);
        }
    }

    private void assertEntity(Class<?> entity, String table) throws Exception {
        assertThat(BaseEntity.class.isAssignableFrom(entity)).isTrue();
        assertThat(entity.getAnnotation(TableName.class).value()).isEqualTo(table);
        assertThat(Arrays.stream(entity.getDeclaredFields())
            .anyMatch(field -> field.isAnnotationPresent(TableId.class))).isTrue();
        Field version = entity.getDeclaredField("version");
        Field delFlag = entity.getDeclaredField("delFlag");
        assertThat(version.isAnnotationPresent(Version.class)).isTrue();
        assertThat(delFlag.isAnnotationPresent(TableLogic.class)).isTrue();
    }

    private List<Element> elements(Document document, String tag) {
        NodeList nodes = document.getElementsByTagName(tag);
        return java.util.stream.IntStream.range(0, nodes.getLength())
            .mapToObj(index -> (Element) nodes.item(index))
            .toList();
    }
}
