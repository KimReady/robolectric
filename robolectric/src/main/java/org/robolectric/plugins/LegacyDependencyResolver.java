package org.robolectric.plugins;

import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Properties;
import javax.annotation.Priority;
import javax.inject.Inject;
import org.robolectric.internal.dependency.DependencyJar;
import org.robolectric.internal.dependency.DependencyResolver;
import org.robolectric.internal.dependency.LocalDependencyResolver;
import org.robolectric.internal.dependency.PropertiesDependencyResolver;
import org.robolectric.res.Fs;
import org.robolectric.util.ReflectionHelpers;

/**
 * Robolectric's historical dependency resolver (which is currently still the default), which is
 * used by {@link org.robolectric.plugins.DefaultSdkProvider} to locate SDK jars.
 *
 * Robolectric will attempt to find SDK jars in the following order:
 *
 * 1. If the system property `robolectric-deps.properties` is set, then Robolectric will look for
 *    a file with the specified path containing SDK references as described
 *    {@link PropertiesDependencyResolver here}.
 * 1. If the system property `robolectric.dependency.dir` is set, then Robolectric will look for
 *    SDK jars in the given directory with Maven artifact-style names (e.g.
 *    `android-all-7.1.0_r7-robolectric-r1.jar`).
 * 1. If the system property `robolectric.offline` is `true`, then Robolectric will look for SDK
 *    jars in the current working directory with Maven artifact-style names.
 * 1. If a resource file named `robolectric-deps.properties` is found on the classpath, then
 *    Robolectric will resolve SDKs with that file as described
 *    {@link PropertiesDependencyResolver here}.
 * 1. Otherwise the jars will be downloaded from Maven Central and cached locally.
 *
 * If you require a hermetic build, we recommend either specifying the `robolectric.dependency.dir`
 * system property, or providing your own {@link org.robolectric.pluginapi.SdkProvider}.
 */
@AutoService(DependencyResolver.class)
@Priority(Integer.MIN_VALUE)
@SuppressWarnings("NewApi")
public class LegacyDependencyResolver implements DependencyResolver {

  private final DependencyResolver delegate;

  @Inject
  public LegacyDependencyResolver(Properties properties) {
    this(properties, LegacyDependencyResolver.class.getClassLoader());
  }

  @VisibleForTesting
  LegacyDependencyResolver(Properties properties, ClassLoader classLoader) {
    this.delegate = pickOne(properties, classLoader);
  }

  private static DependencyResolver pickOne(Properties properties, ClassLoader classLoader) {
    String propPath = properties.getProperty("robolectric-deps.properties");
    if (propPath != null) {
      return new PropertiesDependencyResolver(Paths.get(propPath));
    }

    String dependencyDir = properties.getProperty("robolectric.dependency.dir");
    if (dependencyDir != null
        || Boolean.parseBoolean(properties.getProperty("robolectric.offline"))) {
      return new LocalDependencyResolver(new File(dependencyDir == null ? "." : dependencyDir));
    }

    URL buildPathPropertiesUrl = classLoader.getResource("robolectric-deps.properties");
    if (buildPathPropertiesUrl != null) {
      return new PropertiesDependencyResolver(Paths.get(Fs.toUri(buildPathPropertiesUrl)));
    }

    Class<?> clazz = ReflectionHelpers.loadClass(classLoader,
        "org.robolectric.plugins.CachedMavenDependencyResolver");
    return (DependencyResolver) ReflectionHelpers.callConstructor(clazz);
  }

  @Override
  public URL getLocalArtifactUrl(DependencyJar dependency) {
    return delegate.getLocalArtifactUrl(dependency);
  }

  @Override
  public URL[] getLocalArtifactUrls(DependencyJar dependency) {
    return delegate.getLocalArtifactUrls(dependency);
  }
}
