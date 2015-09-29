package de.adito.maven.plugin.jarsign;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.jarsigner.*;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.javatool.*;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.*;

/**
 * Utility for signing.
 *
 * @author j.boesl, 27.09.15
 */
public class SignUtility
{

  private SignUtility()
  {
  }

  static Set<Path> getWorkPaths(MavenProject pProject, String pJarDirectory, String pTypes) throws IOException
  {
    Path path = Paths.get(pJarDirectory);
    if (!path.isAbsolute())
      path = pProject.getBasedir().toPath().resolve(path);

    final PathMatcher matcher = path.getFileSystem().getPathMatcher("glob:**.{" + pTypes + "}");
    final Set<Path> workFiles = new HashSet<>();

    if (Files.exists(path))
    {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>()
      {
        @Override
        public FileVisitResult visitFile(Path pFilePath, BasicFileAttributes attrs) throws IOException
        {
          FileVisitResult visitResult = super.visitFile(pFilePath, attrs);
          if (matcher.matches(pFilePath))
            workFiles.add(pFilePath);
          return visitResult;
        }
      });
    }

    return workFiles;
  }


  static void updateManifest(Log pLogger, Map<String, String> pAdditionalManifestEntries, Path pPath) throws IOException
  {
    String manifestPath = "META-INF/MANIFEST.MF";

    Manifest manifest;
    byte[] zipContent = ZipUtil.unpackEntry(pPath.toFile(), manifestPath);
    if (zipContent == null)
      manifest = new Manifest();
    else
      try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipContent))
      {
        manifest = new Manifest(inputStream);
      }

    Attributes mainAttributes = manifest.getMainAttributes();
    if (pAdditionalManifestEntries != null)
      for (Map.Entry<String, String> entry : pAdditionalManifestEntries.entrySet())
        mainAttributes.putValue(entry.getKey(), entry.getValue());

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream())
    {
      manifest.write(outputStream);
      ZipUtil.replaceEntry(pPath.toFile(), manifestPath, outputStream.toByteArray());
      pLogger.info("Updated manifest for " + pPath + ".");
    }
  }


  static void sign(DefaultJarSigner pJarSigner, String pAlias, String pKeystore, String pStorepass,
                   String pKeypass, String pTsa, Path pArchivePath)
      throws CommandLineException, JavaToolException, MojoExecutionException
  {
    JarSignerSignRequest signRequest = new JarSignerSignRequest();
    _setup(signRequest, pAlias, pKeystore, pStorepass, pArchivePath);
    signRequest.setKeypass(pKeypass);
    signRequest.setTsaLocation(pTsa);
    _execute(pJarSigner, signRequest);
  }

  static void verify(DefaultJarSigner pJarSigner, String pAlias, String pKeystore, String pStorepass,
                     Path pArchivePath)
      throws JavaToolException, CommandLineException, MojoExecutionException
  {
    JarSignerVerifyRequest verifyRequest = new JarSignerVerifyRequest();
    _setup(verifyRequest, pAlias, pKeystore, pStorepass, pArchivePath);
    verifyRequest.setVerbose(true);
    verifyRequest.setArguments("-strict", "-verbose:summary");
    _execute(pJarSigner, verifyRequest);
  }

  private static void _setup(AbstractJarSignerRequest pRequest, String pAlias, String pKeystore, String pStorepass,
                             Path pArchivePath)
  {
    pRequest.setKeystore(pKeystore);
    pRequest.setAlias(pAlias);
    pRequest.setStorepass(pStorepass);
    pRequest.setArchive(pArchivePath.toFile());
  }

  private static void _execute(JarSigner pJarSigner, AbstractJarSignerRequest pRequest)
      throws CommandLineException, JavaToolException, MojoExecutionException
  {
    JavaToolResult result = pJarSigner.execute(pRequest);
    CommandLineException executionException = result.getExecutionException();
    if (executionException != null)
      throw executionException;
    int exitCode = result.getExitCode();
    if (exitCode != 0)
      throw new MojoExecutionException("Wrong exit code '" + exitCode + "'. Jar signing or verifying failed for "
                                           + pRequest.getArchive().getAbsolutePath() + ".");
  }

}
