package io.mvnpm.maven.locker.mojos;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.TEST;

import java.util.Arrays;
import java.util.List;

import io.mvnpm.maven.locker.DependenciesLockFileAccessor;
import io.mvnpm.maven.locker.DependencySetConfiguration;
import io.mvnpm.maven.locker.Filters;
import io.mvnpm.maven.locker.LockedDependencies;
import io.mvnpm.maven.locker.pom.DependenciesLockFilePom;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.artifact.filter.StrictPatternIncludesArtifactFilter;

@Mojo(name = "check", defaultPhase = VALIDATE, requiresDependencyResolution = TEST)
public final class CheckMojo extends AbstractDependencyLockMojo {

  @Parameter private DependencySet[] dependencySets = new DependencySet[0];

  @Override
  public void execute() throws MojoExecutionException {
    DependenciesLockFileAccessor lockFile = lockFile();
    if (!lockFile.exists()) {
      throw new MojoExecutionException(
          "No lock file found, create one by running 'mvn"
              + " se.vandmo:dependency-lock-maven-plugin:lock'");
    }
    List<DependencySetConfiguration> dependencySetConfigurations =
        unmodifiableList(Arrays.stream(dependencySets).map(this::transform).collect(toList()));

    LockedDependencies lockedDependencies =
            DependenciesLockFilePom.from(lockFile, pomMinimums(), getLog()).read();
    Filters filters = new Filters(dependencySetConfigurations, projectVersion());
    LockedDependencies.Diff diff = lockedDependencies.compareWith(projectDependencies(), filters);
    if (diff.equals()) {
      getLog().info("Actual dependencies matches locked dependencies");
    } else {
      diff.logTo(getLog());
      throw new MojoExecutionException("Dependencies differ");
    }
  }

  private DependencySetConfiguration transform(DependencySet dependencySet) {
    return new DependencySetConfiguration(
        new StrictPatternIncludesArtifactFilter(asList(dependencySet.includes)),
        new StrictPatternIncludesArtifactFilter(asList(dependencySet.excludes)),
        transformVersion(dependencySet.version),
        transformIntegrity(dependencySet.integrity),
        dependencySet.allowMissing,
        dependencySet.allowExtraneous);
  }

  private static DependencySetConfiguration.Integrity transformIntegrity(
      DependencySet.Integrity integrity) {
    if (integrity == null) {
      return null;
    }
    switch (integrity) {
      case check:
        return DependencySetConfiguration.Integrity.check;
      case ignore:
        return DependencySetConfiguration.Integrity.ignore;
      default:
        throw new RuntimeException("Invalid enum value encountered");
    }
  }

  private static DependencySetConfiguration.Version transformVersion(String version) {
    if (version == null) {
      return null;
    }
    switch (version) {
      case "check":
        return DependencySetConfiguration.Version.check;
      case "ignore":
        return DependencySetConfiguration.Version.ignore;
      case "use-project-version":
        return DependencySetConfiguration.Version.useProjectVersion;
      case "snapshot":
        return DependencySetConfiguration.Version.snapshot;
      default:
        throw new RuntimeException("Invalid value for version configuration");
    }
  }
}
