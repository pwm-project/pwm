package password.pwm;

import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.util.Collections;
import java.util.Set;

public class PwmTestUtilities {
    public static <T extends Class> Set<T> findAllImplementingClasses(final String packagePrefix, Class<T> type) {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage(packagePrefix))
                .setScanners(new SubTypesScanner(),
                        new TypeAnnotationsScanner(),
                        new FieldAnnotationsScanner()
                ));


        Set<T> classes = (Set<T>) reflections.getSubTypesOf(type.getClass());
        return Collections.<T>unmodifiableSet(classes);
    }
}
