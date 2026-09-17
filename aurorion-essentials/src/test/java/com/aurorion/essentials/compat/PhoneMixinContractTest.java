package com.aurorion.essentials.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Verifica os pontos de integracao contra o jar real, sem carrega-lo nem exigir um cliente grafico. */
class PhoneMixinContractTest {
    @Test
    void nameFormattingHooksExistInTheInstalledPhoneVersion() throws IOException {
        ClassNode mixin = readMixin("PhoneDisplayNameMixin");
        try (ZipFile phone = phoneJar()) {
            for (String target : targets(mixin)) {
                ClassNode type = readPhoneClass(phone, target);
                assertTrue(type.methods.stream().anyMatch(method -> method.name.equals("trimText")
                        && method.desc.equals("(Ljava/lang/String;I)Ljava/lang/String;")), target);
            }
        }
    }

    @Test
    void contactAndCallHooksReadTheNameOnlyInsideRenderingMethods() throws IOException {
        try (ZipFile phone = phoneJar()) {
            for (String name : List.of("PhoneContactNameMixin", "PhoneCallingNameMixin", "PhoneIncomingNameMixin")) {
                ClassNode mixin = readMixin(name);
                ClassNode type = readPhoneClass(phone, targets(mixin).getFirst());
                for (var handler : mixin.methods) {
                    if (handler.visibleAnnotations == null) continue;
                    for (var annotation : handler.visibleAnnotations) {
                        if (!annotation.desc.endsWith("/Redirect;")) continue;
                        @SuppressWarnings("unchecked")
                        List<String> methods = (List<String>) value(annotation, "method");
                        AnnotationNode at = (AnnotationNode) value(annotation, "at");
                        String target = (String) value(at, "target");
                        for (String methodName : methods) {
                            var method = type.methods.stream().filter(m -> m.name.equals(methodName)).findFirst().orElseThrow();
                            boolean found = false;
                            for (var instruction : method.instructions) {
                                if (instruction instanceof FieldInsnNode field
                                        && target.equals("L" + field.owner + ";" + field.name + ":" + field.desc)) {
                                    found = true;
                                }
                            }
                            assertTrue(found, name + ": " + methodName + " -> " + target);
                        }
                    }
                }
            }
        }
    }

    /**
     * O mesmo erro que derrubou o cliente no banco: {@code @Shadow} de um campo que o alvo apenas
     * herda. O Mixin exige o campo declarado na propria classe, e aqui {@code defaultRequire} e 1,
     * entao a falha e fatal em vez de silenciosa.
     */
    @Test
    void shadowedFieldsAreDeclaredByTheTargetItselfAndNotInherited() throws IOException {
        try (ZipFile phone = phoneJar()) {
            for (String name : List.of("PhoneContactNameMixin", "PhoneCallingNameMixin", "PhoneIncomingNameMixin")) {
                ClassNode mixin = readMixin(name);
                ClassNode type = readPhoneClass(phone, targets(mixin).getFirst());
                for (var field : mixin.fields) {
                    if (!hasAnnotation(field.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;")) continue;
                    assertTrue(type.fields.stream().anyMatch(candidate -> candidate.name.equals(field.name)
                                    && candidate.desc.equals(field.desc)),
                            name + ": @Shadow " + field.name + field.desc + " nao e declarado por " + type.name);
                }
            }
        }
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations != null && annotations.stream().anyMatch(annotation -> annotation.desc.equals(descriptor));
    }

    private static ZipFile phoneJar() throws IOException {
        String path = System.getenv("AURORION_PHONE_JAR");
        assumeTrue(path != null && !path.isBlank(), "Defina AURORION_PHONE_JAR para validar a versao instalada do telefone.");
        return new ZipFile(path);
    }

    private static ClassNode readPhoneClass(ZipFile phone, String name) throws IOException {
        var entry = phone.getEntry(name.replace('.', '/') + ".class");
        assertNotNull(entry, name);
        try (var input = phone.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static ClassNode readMixin(String name) throws IOException {
        try (var input = PhoneMixinContractTest.class.getResourceAsStream("/com/aurorion/essentials/mixin/" + name + ".class")) {
            assertNotNull(input, name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> targets(ClassNode mixin) {
        var annotation = mixin.invisibleAnnotations.stream().filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        return (List<String>) value(annotation, "targets");
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        throw new AssertionError(annotation.desc + " sem " + key);
    }
}
