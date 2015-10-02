package de.adito.maven.repositoryjarsignplugin;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.jarsigner.*;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.javatool.JavaToolException;
import org.codehaus.plexus.digest.Digester;
import org.codehaus.plexus.logging.console.ConsoleLogger;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mojo for signing jars in a folder.
 *
 * @author PaL
 *         Date: 01.02.15
 *         Time. 21:15
 */
@Mojo(name = "sign", requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class SignMojo extends AbstractMojo
{

  @Component
  private MavenProject project;

  @Component(hint = "sha1")
  private Digester digester;

  /**
   * Local repository.
   */
  @Parameter(readonly = true, required = true, defaultValue = "${localRepository}")
  private ArtifactRepository localRepository;

  /**
   * Identifier for the jar-signing-cache. Signed jars are shared between all modules with the same id.
   */
  @Parameter(required = true, property = "repository.jarsign.id")
  private String id;

  /**
   * The directory where the jars to be signed are in.
   */
  @Parameter(required = true, property = "repository.jarsign.directory")
  private String jarDirectory;

  /**
   * A comma separated string with all extensions that shall be signed. Default is <tt>jar</tt> only.
   */
  @Parameter(defaultValue = "jar", property = "repository.jarsign.types")
  private String types;

  /**
   * If <i>true</i> all jars are signed no matter whether already signed or not.
   */
  @Parameter(defaultValue = "false", property = "repository.jarsign.force")
  private boolean forceSign;

  /**
   * All entries in this map are added to the jars manifests.
   */
  @Parameter
  private Map<String, String> additionalManifestEntries;

  /**
   * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
   */
  @Parameter(property = "jarsigner.alias")
  private String alias;

  /**
   * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
   */
  @Parameter(property = "jarsigner.keystore")
  private String keystore;

  /**
   * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
   */
  @Parameter(property = "jarsigner.storepass")
  private String storepass;

  /**
   * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
   */
  @Parameter(property = "jarsigner.keypass")
  private String keypass;

  /**
   * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
   */
  @Parameter(property = "jarsigner.tsa")
  private String tsa;

  /**
   * Defines whether the jars shall be repacked by the pack200 utility. This might be necessary when using pack200.
   */
  @Parameter(defaultValue = "false")
  private boolean repack;

  /**
   * If <i>true</i> the jars are packed with pack200 after the jars were signed.
   */
  @Parameter(defaultValue = "false")
  private boolean pack200;


  public void execute() throws MojoExecutionException
  {
    jarDirectory = _normalizeFilePath(jarDirectory);
    keystore = _normalizeFilePath(keystore);

    int availableProcessors = Runtime.getRuntime().availableProcessors();
    ExecutorService executorService = Executors.newFixedThreadPool(availableProcessors);

    try
    {
      Path cachePath = _getCachePath();
      if (_isNewKeyStoreKey(cachePath))
        forceSign = true;

      final DefaultJarSigner jarSigner = new DefaultJarSigner();
      jarSigner.enableLogging(new ConsoleLogger());

      Set<Path> workFiles = SignUtility.getWorkPaths(project, jarDirectory, types);

      List<SignCandidate> candidates = _sign(jarSigner, cachePath, workFiles);

      final AtomicInteger signedCount = new AtomicInteger();
      final AtomicInteger verifiedCount = new AtomicInteger();

      List<Future> futureList = new LinkedList<>();
      // update signing directory and verify
      for (final SignCandidate candidate : candidates)
      {
        futureList.add(executorService.submit(new Callable<Void>()
        {
          @Override
          public Void call() throws Exception
          {
            _verify(jarSigner, candidate, signedCount, verifiedCount);
            return null;
          }
        }));
      }
      // wait till verification finshed.
      for (Future future : futureList)
        future.get();

      getLog().info(signedCount + " jars have been signed.");
      getLog().info(verifiedCount + " jars have been verified.");
    }
    catch (Exception e)
    {
      if (e instanceof MojoExecutionException)
        throw (MojoExecutionException) e;
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }


  private List<SignCandidate> _sign(DefaultJarSigner pJarSigner, Path pCachePath, Set<Path> pWorkFiles)
      throws IOException, MojoExecutionException, CommandLineException, JavaToolException, InterruptedException
  {
    SignChecksumHelper signChecksumHelper = new SignChecksumHelper(getLog(), digester);

    List<SignCandidate> candidates = new ArrayList<>();

    // update shared directory
    synchronized (id.intern())
    {
      for (Path archivePath : pWorkFiles)
      {
        SignCandidate candidate = new SignCandidate(archivePath, pCachePath, signChecksumHelper, forceSign, repack, pack200);
        candidates.add(candidate);
        switch (candidate.getType())
        {
          case NEW:
            getLog().info("Signing " + candidate.getArchivePath() + ".");

            // install default checksum
            signChecksumHelper.installSignChecksum(candidate.getCheckSumPath(), archivePath, repack);

            JarSignerUtil.unsignArchive(archivePath.toFile());

            SignUtility.updateManifest(getLog(), additionalManifestEntries, archivePath);

            if (repack)
              PackUtility.repack(archivePath);


            SignUtility.sign(pJarSigner, alias, keystore, storepass, keypass, tsa, archivePath);

            if (pack200)
              archivePath = PackUtility.pack(archivePath);

            Files.copy(archivePath, candidate.getCopyPath(), StandardCopyOption.REPLACE_EXISTING);

            // install signed checksum
            signChecksumHelper.installSignChecksum(candidate.getSignedCheckSumPath(), archivePath, repack);

            break;
          case CACHED:
          case SIGNED:
            break;
          default:
            throw new MojoExecutionException("unknown type: " + candidate.getType());
        }

        if (Thread.interrupted())
          throw new InterruptedException();
      }
    }
    return candidates;
  }


  private void _verify(DefaultJarSigner pJarSigner, SignCandidate pCandidate, AtomicInteger pSignedCount,
                       AtomicInteger pVerifiedCount)
      throws IOException, JavaToolException, CommandLineException, MojoExecutionException, InterruptedException
  {
    Path archivePath = pack200 ? PackUtility.getPackPath(pCandidate.getArchivePath()) : pCandidate.getArchivePath();

    switch (pCandidate.getType())
    {
      case NEW:
        pSignedCount.incrementAndGet();

        // fall through
      case CACHED:
        if (pCandidate.getType() != SignCandidate.TYPE.NEW)
          synchronized (id.intern())
          {
            Files.copy(pCandidate.getCopyPath(), archivePath, StandardCopyOption.REPLACE_EXISTING);
          }

        // fall through
      default:
        if (pack200)
        {
          Path unpackPath = pCandidate.getArchivePath();
          PackUtility.unpack(archivePath, unpackPath);
          archivePath = unpackPath;
        }

        SignUtility.verify(pJarSigner, alias, keystore, storepass, archivePath);

        if (pack200)
          Files.delete(archivePath);

        pVerifiedCount.incrementAndGet();
        break;
    }

    if (Thread.interrupted())
      throw new InterruptedException();
  }

  private boolean _isNewKeyStoreKey(Path pCachePath) throws IOException, MojoExecutionException
  {
    byte[] digest = SignUtility.getKeyStoreKeyDigest(keystore, alias, storepass, keypass, digester);
    Path kskcPath = pCachePath.resolve("_key_store_key_digest." + digester.getAlgorithm().toLowerCase());
    if (Files.exists(kskcPath))
    {
      byte[] bytes = Files.readAllBytes(kskcPath);
      if (Arrays.equals(digest, bytes))
        return false;
    }
    Files.write(kskcPath, digest);
    return true;
  }

  private Path _getCachePath() throws IOException
  {
    Path cachePath = Paths.get(localRepository.getBasedir()).getParent().resolve("jarsign-cache").resolve(id);
    return Files.createDirectories(cachePath);
  }

  private String _normalizeFilePath(String pPath)
  {
    return pPath.replaceFirst("^~/", System.getProperty("user.home") + "/");
  }

}