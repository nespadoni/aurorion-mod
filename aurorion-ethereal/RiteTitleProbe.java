import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;

class RiteTitleProbe {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of(args[0]));
        var rotation = Pattern.compile("ROTATION\\.rotationY\\(([^;]*event\\.getCamera\\(\\)\\.getYRot\\(\\)[^;]*)\\)\\);").matcher(source);
        if (!rotation.find()) throw new AssertionError("Title rotation not found");
        String angle = rotation.group(1).replace("event.getCamera().getYRot()", "yaw")
                .replace("Mth.DEG_TO_RAD", "((float) java.lang.Math.PI / 180F)")
                .replace("Mth.PI", "(float) java.lang.Math.PI");
        var scale = Pattern.compile("pose\\.scale\\(([^;]*-scale[^;]*)\\);").matcher(source);
        if (!scale.find()) throw new AssertionError("Title scale not found");
        try (JShell shell = JShell.builder().executionEngine("local").build()) {
            shell.addToClasspath(args[1]);
            check(shell, "import org.joml.*;");
            check(shell, "float scale = .115F;");
            String probe = """
                    int failures = 0;
                    for (float yaw : new float[]{0, 45, 90, 135, 180, 225, 270, 315}) {
                        for (float pitch : new float[]{-75, -30, 0, 30, 75}) {
                            Matrix4f view = new Matrix4f().rotateX((float) java.lang.Math.toRadians(pitch))
                                    .rotateY((float) java.lang.Math.toRadians(yaw + 180));
                            Matrix4f model = new Matrix4f().rotateY(ANGLE).scale(SCALE);
                            Matrix4f clip = view.mul(model);
                            Vector3f a = clip.transformPosition(new Vector3f(0, 0, 0));
                            Vector3f b = clip.transformPosition(new Vector3f(0, 8, 0));
                            Vector3f c = clip.transformPosition(new Vector3f(6, 8, 0));
                            float area = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
                            Vector3f textOffset = clip.transformDirection(new Vector3f(0, 0, .03F));
                            boolean visible = area > 0 && c.x > b.x && a.y > b.y;
                            boolean foregroundIsCloser = textOffset.z > 0;
                            if (!visible || !foregroundIsCloser) failures++;
                            if (pitch == 0) System.out.printf("yaw=%3.0f frontFacing=%s foregroundCloser=%s area=%.5f%n",
                                    yaw, visible, foregroundIsCloser, area);
                        }
                    }
                    if (failures != 0) throw new AssertionError(failures + "/40 title transforms failed");
                    System.out.println("PASS: 40 title transforms (8 yaw x 5 pitch), winding and shadow depth");
                    """.replace("ANGLE", angle).replace("SCALE", scale.group(1));
            check(shell, "{ " + probe + " }");
        }
    }

    private static void check(JShell shell, String code) {
        for (var event : shell.eval(code)) {
            if (event.status() == Snippet.Status.REJECTED) {
                shell.diagnostics(event.snippet()).forEach(d -> System.err.println(d.getMessage(null)));
                throw new AssertionError("Probe rejected");
            }
            if (event.exception() != null) throw new AssertionError(event.exception());
        }
    }
}
