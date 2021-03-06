package qub;

public interface QubPackTests
{
    static void test(TestRunner runner)
    {
        runner.testGroup(QubPack.class, () ->
        {
            runner.testGroup("main(String[])", () ->
            {
                runner.test("with null", (Test test) ->
                {
                    test.assertThrows(new PreConditionFailure("arguments cannot be null."),
                        () -> QubPack.main(null));
                });
            });

            runner.testGroup("getParameters(FakeDesktopProcess)", () ->
            {
                runner.test("with null", (Test test) ->
                {
                    test.assertThrows(() -> QubPack.getParameters(null),
                        new PreConditionFailure("process cannot be null."));
                });

                runner.test("with -?", (Test test) ->
                {
                    try (final FakeDesktopProcess process = FakeDesktopProcess.create("-?"))
                    {
                        final QubPackParameters parameters = QubPack.getParameters(process);
                        test.assertNull(parameters);

                        test.assertEqual(-1, process.getExitCode());
                        test.assertEqual(
                            Iterable.create(
                                "Usage: qub-pack [[--folder=]<folder-to-pack>] [--packjson] [--parallelpack] [--testjson] [--buildjson] [--warnings=<show|error|hide>] [--verbose] [--profiler] [--help]",
                                "  Used to package source and compiled code in source code projects.",
                                "  --folder:       The folder to pack. Defaults to the current folder.",
                                "  --packjson:     Whether or not to read and write a pack.json file. Defaults to true.",
                                "  --parallelpack: Whether or not the jar files will be packaged in parallel. Defaults to true.",
                                "  --testjson:     Whether or not to write the test results to a test.json file.",
                                "  --buildjson:    Whether or not to read and write a build.json file. Defaults to true.",
                                "  --warnings:     How to handle build warnings. Can be either \"show\", \"error\", or \"hide\". Defaults to \"show\".",
                                "  --verbose(v):   Whether or not to show verbose logs.",
                                "  --profiler:     Whether or not this application should pause before it is run to allow a profiler to be attached.",
                                "  --help(?):      Show the help message for this application."),
                            Strings.getLines(process.getOutputWriteStream().getText().await()));
                        test.assertEqual(
                            Iterable.create(),
                            Strings.getLines(process.getErrorWriteStream().getText().await()));
                    }
                });

                runner.test("with no command line arguments", (Test test) ->
                {
                    try (final FakeDesktopProcess process = FakeDesktopProcess.create())
                    {
                        process.setDefaultCurrentFolder("/current/folder/");
                        
                        final InMemoryFileSystem fileSystem = process.getFileSystem();

                        final Folder currentFolder = process.getCurrentFolder();

                        final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                        final QubProjectFolder qubBuildProjectFolder = qubFolder.getProjectFolder("qub", "build-java").await();
                        final File qubBuildCompiledSourcesFile = qubBuildProjectFolder.getCompiledSourcesFile("7").await();
                        qubBuildCompiledSourcesFile.create().await();
                        final Folder qubBuildDataFolder = qubBuildProjectFolder.getProjectDataFolder().await();
                        final QubProjectFolder qubTestProjectFolder = qubFolder.getProjectFolder("qub", "test-java").await();
                        final File qubTestCompiledSourcesFile = qubTestProjectFolder.getCompiledSourcesFile("8").await();
                        qubTestCompiledSourcesFile.create().await();
                        final Folder qubTestDataFolder = qubTestProjectFolder.getProjectDataFolder().await();

                        process.getTypeLoader()
                            .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                            .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);

                        final QubPackParameters parameters = QubPack.getParameters(process);
                        test.assertNotNull(parameters);
                        test.assertTrue(parameters.getBuildJson());
                        test.assertEqual(Coverage.None, parameters.getCoverage());
                        test.assertSame(process.getDefaultApplicationLauncher(), parameters.getDefaultApplicationLauncher());
                        test.assertSame(process.getEnvironmentVariables(), parameters.getEnvironmentVariables());
                        test.assertSame(process.getErrorWriteStream(), parameters.getErrorWriteStream());
                        test.assertEqual(currentFolder, parameters.getFolderToBuild());
                        test.assertEqual(currentFolder, parameters.getFolderToPack());
                        test.assertEqual(currentFolder, parameters.getFolderToTest());
                        test.assertEqual(process.getJVMClasspath().await(), parameters.getJvmClassPath());
                        test.assertSame(process.getOutputWriteStream(), parameters.getOutputWriteStream());
                        test.assertNull(parameters.getPattern());
                        test.assertTrue(parameters.getPackJson());
                        test.assertTrue(parameters.getParallelPack());
                        test.assertSame(process.getProcessFactory(), parameters.getProcessFactory());
                        test.assertFalse(parameters.getProfiler());
                        test.assertEqual(qubBuildDataFolder, parameters.getQubBuildDataFolder());
                        test.assertEqual(qubTestDataFolder, parameters.getQubTestDataFolder());
                        test.assertTrue(parameters.getTestJson());
                        final VerboseCharacterToByteWriteStream verbose = parameters.getVerbose();
                        test.assertNotNull(verbose);
                        test.assertFalse(verbose.isVerbose());
                        test.assertEqual(Warnings.Show, parameters.getWarnings());

                        test.assertEqual("", process.getOutputWriteStream().getText().await());
                        test.assertEqual("", process.getErrorWriteStream().getText().await());
                    }
                });
            });

            runner.testGroup("run(QubPackParameters)", () ->
            {
                runner.test("with null", (Test test) ->
                {
                    test.assertThrows(() -> QubPack.run(null),
                        new PreConditionFailure("parameters cannot be null."));
                });

                runner.test("with no project.json file", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder);
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final File qubTestCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "test-java", "8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader);

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "ERROR: The file at \"/current/folder/project.json\" doesn't exist."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(1, exitCode);
                });

                runner.test("with no source files", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final File projectJsonFile = currentFolder.getFile("project.json").await();
                    projectJsonFile.setContentsAsString(
                        ProjectJSON.create()
                            .setJava(ProjectJSONJava.create())
                            .toString())
                        .await();
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder);
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final File qubTestCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "test-java", "8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader);

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "ERROR: No java source files found in /current/folder/."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(1, exitCode);
                });

                runner.test("with simple success", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final QubProjectFolder qubTestProjectFolder = qubFolder.getProjectFolder("qub", "test-java").await();
                    final File qubTestCompiledSourcesFile = qubTestProjectFolder.getCompiledSourcesFile("8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final File qubTestLogFile = qubTestProjectFolder.getProjectDataFolder().await()
                        .getFile("logs/1.log").await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final File projectJsonFile = currentFolder.getFile("project.json").await();
                    projectJsonFile.setContentsAsString(
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("a")
                            .setVersion("34")
                            .setJava(ProjectJSONJava.create())
                            .toString())
                        .await();
                    final Folder sourcesFolder = currentFolder.getFolder("sources").await();
                    final File aJavaFile = sourcesFolder.getFile("A.java").await();
                    aJavaFile.setContentsAsString("A.java source").await();
                    final Folder outputsFolder = currentFolder.getFolder("outputs").await();
                    final File aClassFile = outputsFolder.getFile("A.class").await();
                    aClassFile.setContentsAsString("A.java bytecode").await();
                    final File aSourcesJarFile = outputsFolder.getFile("a.sources.jar").await();
                    final File aJarFile = outputsFolder.getFile("a.jar").await();
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder)
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addVersion()
                            .setVersionFunctionAutomatically("javac 14.0.1"))
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addOutputFolder(outputsFolder)
                            .addXlintUnchecked()
                            .addXlintDeprecation()
                            .addClasspath(outputsFolder)
                            .addSourceFile(aJavaFile.relativeTo(currentFolder))
                            .setCompileFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath(Iterable.create(outputsFolder.toString(), jvmClassPath))
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(false)
                            .addTestJson(true)
                            .addLogFile(qubTestLogFile)
                            .addOutputFolder(outputsFolder)
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("A")))
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(sourcesFolder)
                            .addCreate()
                            .addJarFile(aSourcesJarFile.relativeTo(outputsFolder))
                            .addContentFilePath(aJavaFile.relativeTo(sourcesFolder))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(aClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically());
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader);

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "Compiling 1 file...",
                            "Running tests...",
                            "",
                            "Creating sources jar file...",
                            "Creating compiled sources jar file..."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(0, exitCode);

                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.java"),
                        Strings.getLines(aSourcesJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.class"),
                        Strings.getLines(aJarFile.getContentsAsString().await()));
                });

                runner.test("with simple success and verbose", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final QubProjectFolder qubTestProjectFolder = qubFolder.getProjectFolder("qub", "test-java").await();
                    final File qubTestCompiledSourcesFile = qubTestProjectFolder.getCompiledSourcesFile("8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final File qubTestLogFile = qubTestProjectFolder.getProjectDataFolder().await()
                        .getFile("logs/1.log").await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final File projectJsonFile = currentFolder.getFile("project.json").await();
                    projectJsonFile.setContentsAsString(
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("a")
                            .setVersion("34")
                            .setJava(ProjectJSONJava.create())
                            .toString())
                        .await();
                    final Folder sourcesFolder = currentFolder.getFolder("sources").await();
                    final File aJavaFile = sourcesFolder.getFile("A.java").await();
                    aJavaFile.setContentsAsString("A.java source").await();
                    final Folder outputsFolder = currentFolder.getFolder("outputs").await();
                    final File aClassFile = outputsFolder.getFile("A.class").await();
                    aClassFile.setContentsAsString("A.java bytecode").await();
                    final File aSourcesJarFile = outputsFolder.getFile("a.sources.jar").await();
                    final File aJarFile = outputsFolder.getFile("a.jar").await();
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder)
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addVersion()
                            .setVersionFunctionAutomatically("javac 14.0.1"))
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addOutputFolder(outputsFolder)
                            .addXlintUnchecked()
                            .addXlintDeprecation()
                            .addClasspath(outputsFolder)
                            .addSourceFile(aJavaFile.relativeTo(currentFolder))
                            .setCompileFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath(Iterable.create(outputsFolder.toString(), jvmClassPath))
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(true)
                            .addTestJson(true)
                            .addLogFile(qubTestLogFile)
                            .addOutputFolder(outputsFolder)
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("A")))
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(sourcesFolder)
                            .addCreate()
                            .addJarFile(aSourcesJarFile.relativeTo(outputsFolder))
                            .addContentFilePath(aJavaFile.relativeTo(sourcesFolder))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(aClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically());
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader)
                        .setVerbose(VerboseCharacterToByteWriteStream.create(output));

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "VERBOSE: Parsing project.json...",
                            "VERBOSE: Getting javac version...",
                            "VERBOSE: Running /current/folder/: javac --version...",
                            "VERBOSE: javac 14.0.1",
                            "VERBOSE: Parsing outputs/build.json...",
                            "VERBOSE: Updating outputs/build.json...",
                            "VERBOSE: Setting project.json...",
                            "VERBOSE: Setting source files...",
                            "VERBOSE: Detecting java source files to compile...",
                            "VERBOSE: Compiling all source files.",
                            "Compiling 1 file...",
                            "VERBOSE: Running /current/folder/: javac -d outputs -Xlint:unchecked -Xlint:deprecation -classpath /current/folder/outputs/ sources/A.java...",
                            "VERBOSE: Compilation finished.",
                            "VERBOSE: Writing build.json file...",
                            "VERBOSE: Done writing build.json file.",
                            "Running tests...",
                            "VERBOSE: Running /current/folder/: java -classpath /current/folder/outputs/;/fake-jvm-classpath qub.ConsoleTestRunner --profiler=false --verbose=true --testjson=true --logfile=/qub/qub/test-java/data/logs/1.log --output-folder=/current/folder/outputs/ --coverage=None A",
                            "",
                            "Creating sources jar file...",
                            "VERBOSE: Running /current/folder/sources/: jar --create --file=a.sources.jar A.java",
                            "VERBOSE: Created /current/folder/outputs/a.sources.jar.",
                            "Creating compiled sources jar file...",
                            "VERBOSE: Running /current/folder/outputs/: jar --create --file=a.jar A.class",
                            "VERBOSE: Created /current/folder/outputs/a.jar."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(0, exitCode);

                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.java"),
                        Strings.getLines(aSourcesJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.class"),
                        Strings.getLines(aJarFile.getContentsAsString().await()));
                });

                runner.test("with inner class in source file", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final QubProjectFolder qubTestProjectFolder = qubFolder.getProjectFolder("qub", "test-java").await();
                    final File qubTestCompiledSourcesFile = qubTestProjectFolder.getCompiledSourcesFile("8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final File qubTestLogFile = qubTestProjectFolder.getProjectDataFolder().await()
                        .getFile("logs/1.log").await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final File projectJsonFile = currentFolder.getFile("project.json").await();
                    projectJsonFile.setContentsAsString(
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("a")
                            .setVersion("34")
                            .setJava(ProjectJSONJava.create())
                            .toString())
                        .await();
                    final Folder sourcesFolder = currentFolder.getFolder("sources").await();
                    final File aJavaFile = sourcesFolder.getFile("A.java").await();
                    aJavaFile.setContentsAsString("A.java source").await();
                    final Folder outputsFolder = currentFolder.getFolder("outputs").await();
                    final File aClassFile = outputsFolder.getFile("A.class").await();
                    aClassFile.setContentsAsString("A.java bytecode").await();
                    final File abClassFile = outputsFolder.getFile("A$B.class").await();
                    final File aSourcesJarFile = outputsFolder.getFile("a.sources.jar").await();
                    final File aJarFile = outputsFolder.getFile("a.jar").await();
                    abClassFile.setContentsAsString("A$B bytecode").await();
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder)
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addVersion()
                            .setVersionFunctionAutomatically("javac 14.0.1"))
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addOutputFolder(outputsFolder)
                            .addXlintUnchecked()
                            .addXlintDeprecation()
                            .addClasspath(outputsFolder)
                            .addSourceFile(aJavaFile.relativeTo(currentFolder))
                            .setCompileFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath(Iterable.create(outputsFolder.toString(), jvmClassPath))
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(false)
                            .addTestJson(true)
                            .addLogFile(qubTestLogFile)
                            .addOutputFolder(outputsFolder)
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("A$B", "A")))
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(sourcesFolder)
                            .addCreate()
                            .addJarFile(aSourcesJarFile.relativeTo(outputsFolder))
                            .addContentFilePath(aJavaFile.relativeTo(sourcesFolder))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(
                                abClassFile.relativeTo(outputsFolder),
                                aClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically());
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader);

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "Compiling 1 file...",
                            "Running tests...",
                            "",
                            "Creating sources jar file...",
                            "Creating compiled sources jar file..."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(0, exitCode);

                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.java"),
                        Strings.getLines(aSourcesJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A$B.class",
                            "A.class"),
                        Strings.getLines(aJarFile.getContentsAsString().await()));
                });

                runner.test("with anonymous classes in source file", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final QubProjectFolder qubTestProjectFolder = qubFolder.getProjectFolder("qub", "test-java").await();
                    final File qubTestCompiledSourcesFile = qubTestProjectFolder.getCompiledSourcesFile("8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final File qubTestLogFile = qubTestProjectFolder.getProjectDataFolder().await()
                        .getFile("logs/1.log").await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final File projectJsonFile = currentFolder.getFile("project.json").await();
                    projectJsonFile.setContentsAsString(
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("a")
                            .setVersion("34")
                            .setJava(ProjectJSONJava.create())
                            .toString())
                        .await();
                    final Folder sourcesFolder = currentFolder.getFolder("sources").await();
                    final File aJavaFile = sourcesFolder.getFile("A.java").await();
                    aJavaFile.setContentsAsString("A.java source").await();
                    final Folder outputsFolder = currentFolder.getFolder("outputs").await();
                    final File aClassFile = outputsFolder.getFile("A.class").await();
                    aClassFile.setContentsAsString("A.java bytecode").await();
                    final File a1ClassFile = outputsFolder.getFile("A$1.class").await();
                    a1ClassFile.setContentsAsString("A$1 bytecode").await();
                    final File a2ClassFile = outputsFolder.getFile("A$2.class").await();
                    a2ClassFile.setContentsAsString("A$2 bytecode").await();
                    final File aJarFile = outputsFolder.getFile("a.jar").await();
                    final File aSourcesJarFile = outputsFolder.getFile("a.sources.jar").await();
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder)
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addVersion()
                            .setVersionFunctionAutomatically("javac 14.0.1"))
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addOutputFolder(outputsFolder)
                            .addXlintUnchecked()
                            .addXlintDeprecation()
                            .addClasspath(outputsFolder)
                            .addSourceFile(aJavaFile.relativeTo(currentFolder))
                            .setCompileFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath(Iterable.create(outputsFolder.toString(), jvmClassPath))
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(false)
                            .addTestJson(true)
                            .addLogFile(qubTestLogFile)
                            .addOutputFolder(outputsFolder)
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("A$1", "A$2", "A")))
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(sourcesFolder)
                            .addCreate()
                            .addJarFile(aSourcesJarFile.relativeTo(outputsFolder))
                            .addContentFilePath(aJavaFile.relativeTo(sourcesFolder))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(
                                a1ClassFile.relativeTo(outputsFolder),
                                a2ClassFile.relativeTo(outputsFolder),
                                aClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically());
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader);

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "Compiling 1 file...",
                            "Running tests...",
                            "",
                            "Creating sources jar file...",
                            "Creating compiled sources jar file..."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(0, exitCode);

                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.java"),
                        Strings.getLines(aSourcesJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A$1.class",
                            "A$2.class",
                            "A.class"),
                        Strings.getLines(aJarFile.getContentsAsString().await()));
                });

                runner.test("with main class in project.json", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final QubProjectFolder qubTestProjectFolder = qubFolder.getProjectFolder("qub", "test-java").await();
                    final File qubTestCompiledSourcesFile = qubTestProjectFolder.getCompiledSourcesFile("8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final File qubTestLogFile = qubTestProjectFolder.getProjectDataFolder().await()
                        .getFile("logs/1.log").await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final File projectJsonFile = currentFolder.getFile("project.json").await();
                    projectJsonFile.setContentsAsString(
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("a")
                            .setVersion("34")
                            .setJava(ProjectJSONJava.create()
                                .setMainClass("A"))
                            .toString())
                        .await();
                    final Folder sourcesFolder = currentFolder.getFolder("sources").await();
                    final File aJavaFile = sourcesFolder.getFile("A.java").await();
                    aJavaFile.setContentsAsString("A.java source").await();
                    final Folder outputsFolder = currentFolder.getFolder("outputs").await();
                    final File aClassFile = outputsFolder.getFile("A.class").await();
                    aClassFile.setContentsAsString("A.java bytecode").await();
                    final File manifestFile = outputsFolder.getFile("META-INF/MANIFEST.MF").await();
                    final File aSourcesJarFile = outputsFolder.getFile("a.sources.jar").await();
                    final File aJarFile = outputsFolder.getFile("a.jar").await();
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder)
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addVersion()
                            .setVersionFunctionAutomatically("javac 14.0.1"))
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addOutputFolder(outputsFolder)
                            .addXlintUnchecked()
                            .addXlintDeprecation()
                            .addClasspath(outputsFolder)
                            .addSourceFile(aJavaFile.relativeTo(currentFolder))
                            .setCompileFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath(Iterable.create(outputsFolder.toString(), jvmClassPath))
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(false)
                            .addTestJson(true)
                            .addLogFile(qubTestLogFile)
                            .addOutputFolder(outputsFolder)
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("A")))
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(sourcesFolder)
                            .addCreate()
                            .addJarFile(aSourcesJarFile.relativeTo(outputsFolder))
                            .addContentFilePath(aJavaFile.relativeTo(sourcesFolder))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aJarFile.relativeTo(outputsFolder))
                            .addManifestFile(manifestFile)
                            .addContentFilePaths(Iterable.create(aClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically());
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader);

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "Compiling 1 file...",
                            "Running tests...",
                            "",
                            "Creating sources jar file...",
                            "Creating compiled sources jar file..."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(0, exitCode);

                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.java"),
                        Strings.getLines(aSourcesJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Manifest File:",
                            manifestFile.toString(),
                            "",
                            "Content Files:",
                            "A.class"),
                        Strings.getLines(aJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Manifest-Version: 1.0",
                            "Main-Class: A"),
                        Strings.getLines(manifestFile.getContentsAsString().await()));
                });

                runner.test("with test folder", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final QubProjectFolder qubTestProjectFolder = qubFolder.getProjectFolder("qub", "test-java").await();
                    final File qubTestCompiledSourcesFile = qubTestProjectFolder.getCompiledSourcesFile("8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final File qubTestLogFile = qubTestProjectFolder.getProjectDataFolder().await()
                        .getFile("logs/1.log").await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final File projectJsonFile = currentFolder.getFile("project.json").await();
                    projectJsonFile.setContentsAsString(
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("a")
                            .setVersion("34")
                            .setJava(ProjectJSONJava.create())
                            .toString())
                        .await();
                    final Folder sourcesFolder = currentFolder.getFolder("sources").await();
                    final File aJavaFile = sourcesFolder.getFile("A.java").await();
                    aJavaFile.setContentsAsString("A.java source").await();
                    final Folder testsFolder = currentFolder.getFolder("tests").await();
                    final File aTestsJavaFile = testsFolder.getFile("ATests.java").await();
                    aTestsJavaFile.setContentsAsString("ATests.java source").await();
                    final Folder outputsFolder = currentFolder.getFolder("outputs").await();
                    final File aClassFile = outputsFolder.getFile("A.class").await();
                    aClassFile.setContentsAsString("A.java bytecode").await();
                    final File aTestsClassFile = outputsFolder.getFile("ATests.class").await();
                    aTestsClassFile.setContentsAsString("ATests.java bytecode").await();
                    final File aSourcesJarFile = outputsFolder.getFile("a.sources.jar").await();
                    final File aTestsJarFile = outputsFolder.getFile("a.tests.jar").await();
                    final File aJarFile = outputsFolder.getFile("a.jar").await();
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder)
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addVersion()
                            .setVersionFunctionAutomatically("javac 14.0.1"))
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addOutputFolder(outputsFolder)
                            .addXlintUnchecked()
                            .addXlintDeprecation()
                            .addClasspath(outputsFolder)
                            .addSourceFiles(aJavaFile, aTestsJavaFile)
                            .setCompileFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath(Iterable.create(outputsFolder.toString(), jvmClassPath))
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(false)
                            .addTestJson(true)
                            .addLogFile(qubTestLogFile)
                            .addOutputFolder(outputsFolder)
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("A", "ATests")))
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(sourcesFolder)
                            .addCreate()
                            .addJarFile(aSourcesJarFile.relativeTo(outputsFolder))
                            .addContentFilePath(aJavaFile.relativeTo(sourcesFolder))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(aClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aTestsJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(aTestsClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically());
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader);

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "Compiling 2 files...",
                            "Running tests...",
                            "",
                            "Creating sources jar file...",
                            "Creating compiled sources jar file...",
                            "Creating compiled tests jar file..."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(0, exitCode);

                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.java"),
                        Strings.getLines(aSourcesJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.class"),
                        Strings.getLines(aJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "ATests.class"),
                        Strings.getLines(aTestsJarFile.getContentsAsString().await()));
                });

                runner.test("with test folder with inner class", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final QubProjectFolder qubTestProjectFolder = qubFolder.getProjectFolder("qub", "test-java").await();
                    final File qubTestCompiledSourcesFile = qubTestProjectFolder.getCompiledSourcesFile("8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final File qubTestLogFile = qubTestProjectFolder.getProjectDataFolder().await()
                        .getFile("logs/1.log").await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final File projectJsonFile = currentFolder.getFile("project.json").await();
                    projectJsonFile.setContentsAsString(
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("a")
                            .setVersion("34")
                            .setJava(ProjectJSONJava.create())
                            .toString())
                        .await();
                    final Folder sourcesFolder = currentFolder.getFolder("sources").await();
                    final File aJavaFile = sourcesFolder.getFile("A.java").await();
                    aJavaFile.setContentsAsString("A.java source").await();
                    final Folder testsFolder = currentFolder.getFolder("tests").await();
                    final File aTestsJavaFile = testsFolder.getFile("ATests.java").await();
                    aTestsJavaFile.setContentsAsString("ATests.java source").await();
                    final Folder outputsFolder = currentFolder.getFolder("outputs").await();
                    final File aClassFile = outputsFolder.getFile("A.class").await();
                    aClassFile.setContentsAsString("A.java bytecode").await();
                    final File aTestsClassFile = outputsFolder.getFile("ATests.class").await();
                    aTestsClassFile.setContentsAsString("ATests.java bytecode").await();
                    final File aTestsBClassFile = outputsFolder.getFile("ATests$B.class").await();
                    aTestsBClassFile.setContentsAsString("ATests.java inner class bytecode").await();
                    final File aSourcesJarFile = outputsFolder.getFile("a.sources.jar").await();
                    final File aTestsJarFile = outputsFolder.getFile("a.tests.jar").await();
                    final File aJarFile = outputsFolder.getFile("a.jar").await();
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder)
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addVersion()
                            .setVersionFunctionAutomatically("javac 14.0.1"))
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addOutputFolder(outputsFolder)
                            .addXlintUnchecked()
                            .addXlintDeprecation()
                            .addClasspath(outputsFolder)
                            .addSourceFiles(aJavaFile, aTestsJavaFile)
                            .setCompileFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath(Iterable.create(outputsFolder.toString(), jvmClassPath))
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(false)
                            .addTestJson(true)
                            .addLogFile(qubTestLogFile)
                            .addOutputFolder(outputsFolder)
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("A", "ATests$B", "ATests")))
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(sourcesFolder)
                            .addCreate()
                            .addJarFile(aSourcesJarFile.relativeTo(outputsFolder))
                            .addContentFilePath(aJavaFile.relativeTo(sourcesFolder))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(aClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aTestsJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(
                                aTestsBClassFile.relativeTo(outputsFolder),
                                aTestsClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically());
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader);

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "Compiling 2 files...",
                            "Running tests...",
                            "",
                            "Creating sources jar file...",
                            "Creating compiled sources jar file...",
                            "Creating compiled tests jar file..."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(0, exitCode);

                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.java"),
                        Strings.getLines(aSourcesJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.class"),
                        Strings.getLines(aJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "ATests$B.class",
                            "ATests.class"),
                        Strings.getLines(aTestsJarFile.getContentsAsString().await()));
                });

                runner.test("with test folder with anonymous class", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final QubProjectFolder qubTestProjectFolder = qubFolder.getProjectFolder("qub", "test-java").await();
                    final File qubTestCompiledSourcesFile = qubTestProjectFolder.getCompiledSourcesFile("8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final File qubTestLogFile = qubTestProjectFolder.getProjectDataFolder().await()
                        .getFile("logs/1.log").await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final File projectJsonFile = currentFolder.getFile("project.json").await();
                    projectJsonFile.setContentsAsString(
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("a")
                            .setVersion("34")
                            .setJava(ProjectJSONJava.create())
                            .toString())
                        .await();
                    final Folder sourcesFolder = currentFolder.getFolder("sources").await();
                    final File aJavaFile = sourcesFolder.getFile("A.java").await();
                    aJavaFile.setContentsAsString("A.java source").await();
                    final Folder testsFolder = currentFolder.getFolder("tests").await();
                    final File aTestsJavaFile = testsFolder.getFile("ATests.java").await();
                    aTestsJavaFile.setContentsAsString("ATests.java source").await();
                    final Folder outputsFolder = currentFolder.getFolder("outputs").await();
                    final File aClassFile = outputsFolder.getFile("A.class").await();
                    aClassFile.setContentsAsString("A.java bytecode").await();
                    final File aTestsClassFile = outputsFolder.getFile("ATests.class").await();
                    aTestsClassFile.setContentsAsString("ATests.java bytecode").await();
                    final File aTests1ClassFile = outputsFolder.getFile("ATests$1.class").await();
                    aTests1ClassFile.setContentsAsString("ATests.java anonymous class bytecode").await();
                    final File aSourcesJarFile = outputsFolder.getFile("a.sources.jar").await();
                    final File aTestsJarFile = outputsFolder.getFile("a.tests.jar").await();
                    final File aJarFile = outputsFolder.getFile("a.jar").await();
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder)
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addVersion()
                            .setVersionFunctionAutomatically("javac 14.0.1"))
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addOutputFolder(outputsFolder)
                            .addXlintUnchecked()
                            .addXlintDeprecation()
                            .addClasspath(outputsFolder)
                            .addSourceFiles(aJavaFile, aTestsJavaFile)
                            .setCompileFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath(Iterable.create(outputsFolder.toString(), jvmClassPath))
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(false)
                            .addTestJson(true)
                            .addLogFile(qubTestLogFile)
                            .addOutputFolder(outputsFolder)
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("A", "ATests$1", "ATests")))
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(sourcesFolder)
                            .addCreate()
                            .addJarFile(aSourcesJarFile.relativeTo(outputsFolder))
                            .addContentFilePath(aJavaFile.relativeTo(sourcesFolder))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(aClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aTestsJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(
                                aTests1ClassFile.relativeTo(outputsFolder),
                                aTestsClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically());
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader);

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "Compiling 2 files...",
                            "Running tests...",
                            "",
                            "Creating sources jar file...",
                            "Creating compiled sources jar file...",
                            "Creating compiled tests jar file..."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(0, exitCode);

                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.java"),
                        Strings.getLines(aSourcesJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.class"),
                        Strings.getLines(aJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "ATests$1.class",
                            "ATests.class"),
                        Strings.getLines(aTestsJarFile.getContentsAsString().await()));
                });

                runner.test("with packjson=false", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final QubProjectFolder qubTestProjectFolder = qubFolder.getProjectFolder("qub", "test-java").await();
                    final File qubTestCompiledSourcesFile = qubTestProjectFolder.getCompiledSourcesFile("8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final File qubTestLogFile = qubTestProjectFolder.getProjectDataFolder().await()
                        .getFile("logs/1.log").await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final File projectJsonFile = currentFolder.getFile("project.json").await();
                    projectJsonFile.setContentsAsString(
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("a")
                            .setVersion("34")
                            .setJava(ProjectJSONJava.create())
                            .toString())
                        .await();
                    final Folder sourcesFolder = currentFolder.getFolder("sources").await();
                    final File aJavaFile = sourcesFolder.getFile("A.java").await();
                    aJavaFile.setContentsAsString("A.java source").await();
                    final Folder testsFolder = currentFolder.getFolder("tests").await();
                    final File aTestsJavaFile = testsFolder.getFile("ATests.java").await();
                    aTestsJavaFile.setContentsAsString("ATests.java source").await();
                    final Folder outputsFolder = currentFolder.getFolder("outputs").await();
                    final File aClassFile = outputsFolder.getFile("A.class").await();
                    aClassFile.setContentsAsString("A.java bytecode").await();
                    final File aTestsClassFile = outputsFolder.getFile("ATests.class").await();
                    aTestsClassFile.setContentsAsString("ATests.java bytecode").await();
                    final File aSourcesJarFile = outputsFolder.getFile("a.sources.jar").await();
                    final File aTestsJarFile = outputsFolder.getFile("a.tests.jar").await();
                    final File aJarFile = outputsFolder.getFile("a.jar").await();
                    final File packJson = outputsFolder.getFile("pack.json").await();
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder)
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addVersion()
                            .setVersionFunctionAutomatically("javac 14.0.1"))
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addOutputFolder(outputsFolder)
                            .addXlintUnchecked()
                            .addXlintDeprecation()
                            .addClasspath(outputsFolder)
                            .addSourceFiles(aJavaFile, aTestsJavaFile)
                            .setCompileFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath(Iterable.create(outputsFolder.toString(), jvmClassPath))
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(false)
                            .addTestJson(true)
                            .addLogFile(qubTestLogFile)
                            .addOutputFolder(outputsFolder)
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("A", "ATests")))
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(sourcesFolder)
                            .addCreate()
                            .addJarFile(aSourcesJarFile.relativeTo(outputsFolder))
                            .addContentFilePath(aJavaFile.relativeTo(sourcesFolder))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(aClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aTestsJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(aTestsClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically());
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader)
                        .setPackJson(false);

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "Compiling 2 files...",
                            "Running tests...",
                            "",
                            "Creating sources jar file...",
                            "Creating compiled sources jar file...",
                            "Creating compiled tests jar file..."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(0, exitCode);

                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.java"),
                        Strings.getLines(aSourcesJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.class"),
                        Strings.getLines(aJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "ATests.class"),
                        Strings.getLines(aTestsJarFile.getContentsAsString().await()));
                    test.assertFalse(packJson.exists().await());
                });

                runner.test("with packjson=true but no existing pack.json file", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final QubProjectFolder qubTestProjectFolder = qubFolder.getProjectFolder("qub", "test-java").await();
                    final File qubTestCompiledSourcesFile = qubTestProjectFolder.getCompiledSourcesFile("8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final File qubTestLogFile = qubTestProjectFolder.getProjectDataFolder().await()
                        .getFile("logs/1.log").await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final File projectJsonFile = currentFolder.getFile("project.json").await();
                    projectJsonFile.setContentsAsString(
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("a")
                            .setVersion("34")
                            .setJava(ProjectJSONJava.create())
                            .toString())
                        .await();
                    final Folder sourcesFolder = currentFolder.getFolder("sources").await();
                    final File aJavaFile = sourcesFolder.getFile("A.java").await();
                    aJavaFile.setContentsAsString("A.java source").await();
                    final Folder testsFolder = currentFolder.getFolder("tests").await();
                    final File aTestsJavaFile = testsFolder.getFile("ATests.java").await();
                    aTestsJavaFile.setContentsAsString("ATests.java source").await();
                    final Folder outputsFolder = currentFolder.getFolder("outputs").await();
                    final File aClassFile = outputsFolder.getFile("A.class").await();
                    aClassFile.setContentsAsString("A.java bytecode").await();
                    final File aTestsClassFile = outputsFolder.getFile("ATests.class").await();
                    aTestsClassFile.setContentsAsString("ATests.java bytecode").await();
                    final File aSourcesJarFile = outputsFolder.getFile("a.sources.jar").await();
                    final File aTestsJarFile = outputsFolder.getFile("a.tests.jar").await();
                    final File aJarFile = outputsFolder.getFile("a.jar").await();
                    final File packJsonFile = outputsFolder.getFile("pack.json").await();
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder)
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addVersion()
                            .setVersionFunctionAutomatically("javac 14.0.1"))
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addOutputFolder(outputsFolder)
                            .addXlintUnchecked()
                            .addXlintDeprecation()
                            .addClasspath(outputsFolder)
                            .addSourceFiles(aJavaFile, aTestsJavaFile)
                            .setCompileFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath(Iterable.create(outputsFolder.toString(), jvmClassPath))
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(false)
                            .addTestJson(true)
                            .addLogFile(qubTestLogFile)
                            .addOutputFolder(outputsFolder)
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("A", "ATests")))
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(sourcesFolder)
                            .addCreate()
                            .addJarFile(aSourcesJarFile.relativeTo(outputsFolder))
                            .addContentFilePath(aJavaFile.relativeTo(sourcesFolder))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(aClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aTestsJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(aTestsClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically());
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader)
                        .setPackJson(true);

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "Compiling 2 files...",
                            "Running tests...",
                            "",
                            "Creating sources jar file...",
                            "Creating compiled sources jar file...",
                            "Creating compiled tests jar file..."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(0, exitCode);

                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.java"),
                        Strings.getLines(aSourcesJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.class"),
                        Strings.getLines(aJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "ATests.class"),
                        Strings.getLines(aTestsJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        PackJSON.create()
                            .setSourceFiles(Iterable.create(
                                PackJSONFile.create(aJavaFile.relativeTo(sourcesFolder), aJavaFile.getLastModified().await())))
                            .setSourceOutputFiles(Iterable.create(
                                PackJSONFile.create(aClassFile.relativeTo(outputsFolder), aClassFile.getLastModified().await())))
                            .setTestOutputFiles(Iterable.create(
                                PackJSONFile.create(aTestsClassFile.relativeTo(outputsFolder), aTestsClassFile.getLastModified().await())))
                            .setProject("a")
                            .toString(JSONFormat.pretty),
                        packJsonFile.getContentsAsString().await());
                });

                runner.test("with packjson=true with existing empty pack.json file", (Test test) ->
                {
                    final InMemoryCharacterToByteStream output = InMemoryCharacterToByteStream.create();
                    final InMemoryCharacterToByteStream error = InMemoryCharacterToByteStream.create();
                    final InMemoryFileSystem fileSystem = InMemoryFileSystem.create(test.getClock());
                    fileSystem.createRoot("/").await();
                    final QubFolder qubFolder = QubFolder.get(fileSystem.getFolder("/qub/").await());
                    final File qubBuildCompiledSourcesFile = qubFolder.getCompiledSourcesFile("qub", "build-java", "7").await();
                    qubBuildCompiledSourcesFile.create().await();
                    final QubProjectFolder qubTestProjectFolder = qubFolder.getProjectFolder("qub", "test-java").await();
                    final File qubTestLogFile = qubTestProjectFolder.getProjectDataFolder().await()
                        .getFile("logs/1.log").await();
                    final File qubTestCompiledSourcesFile = qubTestProjectFolder.getCompiledSourcesFile("8").await();
                    qubTestCompiledSourcesFile.create().await();
                    final Folder currentFolder = fileSystem.getFolder("/current/folder/").await();
                    final File projectJsonFile = currentFolder.getFile("project.json").await();
                    projectJsonFile.setContentsAsString(
                        ProjectJSON.create()
                            .setPublisher("me")
                            .setProject("a")
                            .setVersion("34")
                            .setJava(ProjectJSONJava.create())
                            .toString())
                        .await();
                    final Folder sourcesFolder = currentFolder.getFolder("sources").await();
                    final File aJavaFile = sourcesFolder.getFile("A.java").await();
                    aJavaFile.setContentsAsString("A.java source").await();
                    final Folder testsFolder = currentFolder.getFolder("tests").await();
                    final File aTestsJavaFile = testsFolder.getFile("ATests.java").await();
                    aTestsJavaFile.setContentsAsString("ATests.java source").await();
                    final Folder outputsFolder = currentFolder.getFolder("outputs").await();
                    final File aClassFile = outputsFolder.getFile("A.class").await();
                    aClassFile.setContentsAsString("A.java bytecode").await();
                    final File aTestsClassFile = outputsFolder.getFile("ATests.class").await();
                    aTestsClassFile.setContentsAsString("ATests.java bytecode").await();
                    final File aSourcesJarFile = outputsFolder.getFile("a.sources.jar").await();
                    final File aTestsJarFile = outputsFolder.getFile("a.tests.jar").await();
                    final File aJarFile = outputsFolder.getFile("a.jar").await();
                    final File packJsonFile = outputsFolder.getFile("pack.json").await();
                    packJsonFile.create().await();
                    final String jvmClassPath = "/fake-jvm-classpath";
                    final FakeProcessFactory processFactory = FakeProcessFactory.create(test.getParallelAsyncRunner(), currentFolder)
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addVersion()
                            .setVersionFunctionAutomatically("javac 14.0.1"))
                        .add(new FakeJavacProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addOutputFolder(outputsFolder)
                            .addXlintUnchecked()
                            .addXlintDeprecation()
                            .addClasspath(outputsFolder)
                            .addSourceFiles(aJavaFile, aTestsJavaFile)
                            .setCompileFunctionAutomatically())
                        .add(new FakeConsoleTestRunnerProcessRun()
                            .setWorkingFolder(currentFolder)
                            .addClasspath(Iterable.create(outputsFolder.toString(), jvmClassPath))
                            .addConsoleTestRunnerFullClassName()
                            .addProfiler(false)
                            .addVerbose(false)
                            .addTestJson(true)
                            .addLogFile(qubTestLogFile)
                            .addOutputFolder(outputsFolder)
                            .addCoverage(Coverage.None)
                            .addFullClassNamesToTest(Iterable.create("A", "ATests")))
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(sourcesFolder)
                            .addCreate()
                            .addJarFile(aSourcesJarFile.relativeTo(outputsFolder))
                            .addContentFilePath(aJavaFile.relativeTo(sourcesFolder))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(aClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically())
                        .add(new FakeJarProcessRun()
                            .setWorkingFolder(outputsFolder)
                            .addCreate()
                            .addJarFile(aTestsJarFile.relativeTo(outputsFolder))
                            .addContentFilePaths(Iterable.create(aTestsClassFile.relativeTo(outputsFolder)))
                            .setFunctionAutomatically());
                    final FakeDefaultApplicationLauncher defaultApplicationLauncher = FakeDefaultApplicationLauncher.create(fileSystem);
                    final EnvironmentVariables environmentVariables = EnvironmentVariables.create()
                        .set("QUB_HOME", qubFolder.toString());
                    final FakeTypeLoader typeLoader = FakeTypeLoader.create()
                        .addTypeContainer(QubBuild.class, qubBuildCompiledSourcesFile)
                        .addTypeContainer(QubTest.class, qubTestCompiledSourcesFile);
                    final QubPackParameters parameters = new QubPackParameters(output, error, currentFolder, environmentVariables, processFactory, defaultApplicationLauncher, jvmClassPath, typeLoader)
                        .setPackJson(true);

                    final int exitCode = QubPack.run(parameters);

                    test.assertEqual(
                        Iterable.create(
                            "Compiling 2 files...",
                            "Running tests...",
                            "",
                            "Creating sources jar file...",
                            "Creating compiled sources jar file...",
                            "Creating compiled tests jar file..."),
                        Strings.getLines(output.getText().await()));
                    test.assertEqual("", error.getText().await());
                    test.assertEqual(0, exitCode);

                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.java"),
                        Strings.getLines(aSourcesJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "A.class"),
                        Strings.getLines(aJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        Iterable.create(
                            "Content Files:",
                            "ATests.class"),
                        Strings.getLines(aTestsJarFile.getContentsAsString().await()));
                    test.assertEqual(
                        PackJSON.create()
                            .setSourceFiles(Iterable.create(
                                PackJSONFile.create(aJavaFile.relativeTo(sourcesFolder), aJavaFile.getLastModified().await())))
                            .setSourceOutputFiles(Iterable.create(
                                PackJSONFile.create(aClassFile.relativeTo(outputsFolder), aClassFile.getLastModified().await())))
                            .setTestOutputFiles(Iterable.create(
                                PackJSONFile.create(aTestsClassFile.relativeTo(outputsFolder), aTestsClassFile.getLastModified().await())))
                            .setProject("a")
                            .toString(JSONFormat.pretty),
                        packJsonFile.getContentsAsString().await());
                });
            });
        });
    }
}
