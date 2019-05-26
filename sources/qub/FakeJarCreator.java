package qub;

public class FakeJarCreator extends JarCreator
{
    @Override
    public Result<File> createJarFile(Console console, boolean isVerbose)
    {
        return Result.create(() ->
        {
            final Folder baseFolder = getBaseFolder();
            final String jarName = getJarName();
            final File jarFile = baseFolder.getFile(jarName + ".jar").await();

            try (final CharacterWriteStream writeStream = jarFile.getContentCharacterWriteStream().await())
            {
                final File manifestFile = getManifestFile();
                if (manifestFile != null)
                {
                    writeStream.writeLine("Manifest file:").await();
                    writeStream.writeLine(manifestFile.relativeTo(baseFolder).toString()).await();
                    writeStream.writeLine().await();
                }

                final Iterable<File> files = getFiles();
                if (!Iterable.isNullOrEmpty(files))
                {
                    writeStream.writeLine("Files:").await();
                    for (final File file : files)
                    {
                        writeStream.writeLine(file.relativeTo(baseFolder).toString()).await();
                    }
                    writeStream.writeLine().await();
                }
            }

            return jarFile;
        });

    }
}