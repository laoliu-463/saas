package com.colonel.saas.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans {@code src/main/java} for {@code private final *Mapper} field injections (DDD-BASE-003).
 */
final class CrossDomainMapperScanner {

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern CLASS_NAME = Pattern.compile("(?:public\\s+)?class\\s+(\\w+)");
    private static final Pattern MAPPER_FIELD =
            Pattern.compile("private\\s+final\\s+(\\w+Mapper)\\s+\\w+\\s*;");

    record Injection(String ownerFqcn, String mapperSimpleName) {
        String key() {
            return ownerFqcn + "|" + mapperSimpleName;
        }
    }

    private CrossDomainMapperScanner() {
    }

    static Set<Injection> scanMainSources(Path backendRoot) throws IOException {
        Path mainJava = backendRoot.resolve("src/main/java");
        Set<Injection> injections = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(mainJava)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/mapper/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/testsupport/"))
                    .forEach(path -> {
                        try {
                            injections.addAll(scanFile(path, mainJava));
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to scan " + path, e);
                        }
                    });
        }
        return injections;
    }

    static Set<Injection> crossDomainInjections(Set<Injection> all) {
        Set<Injection> cross = new LinkedHashSet<>();
        for (Injection injection : all) {
            MapperDomainRegistry.Domain owner =
                    MapperDomainRegistry.ownerDomain(injection.ownerFqcn());
            MapperDomainRegistry.Domain mapper =
                    MapperDomainRegistry.mapperDomain(injection.mapperSimpleName());
            if (MapperDomainRegistry.isCrossDomain(owner, mapper)) {
                cross.add(injection);
            }
        }
        return cross;
    }

    private static Set<Injection> scanFile(Path file, Path mainJavaRoot) throws IOException {
        String content = Files.readString(file);
        Matcher packageMatcher = PACKAGE.matcher(content);
        if (!packageMatcher.find()) {
            return Set.of();
        }
        String pkg = packageMatcher.group(1);
        Matcher classMatcher = CLASS_NAME.matcher(content);
        if (!classMatcher.find()) {
            return Set.of();
        }
        String className = classMatcher.group(1);
        String fqcn = pkg + "." + className;

        Set<Injection> result = new LinkedHashSet<>();
        Matcher mapperMatcher = MAPPER_FIELD.matcher(content);
        while (mapperMatcher.find()) {
            result.add(new Injection(fqcn, mapperMatcher.group(1)));
        }
        return result;
    }
}
