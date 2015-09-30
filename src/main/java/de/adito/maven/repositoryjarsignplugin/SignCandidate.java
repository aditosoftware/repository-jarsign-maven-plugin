package de.adito.maven.repositoryjarsignplugin;

import org.apache.maven.plugin.MojoExecutionException;

import java.nio.file.*;

/**
 * Summary for a candidate for signing.
 *
 * @author j.boesl, 27.08.15
 */
public class SignCandidate
{

  private Path archivePath;
  private Path copyPath;
  private Path checkSumPath;
  private Path signedCheckSumPath;
  private TYPE type;

  public SignCandidate(Path pArchivePath, Path pCachePath, SignChecksumHelper pSignChecksumHelper, boolean pForceSign,
                       boolean pRepack, boolean pPack200)
      throws MojoExecutionException
  {
    archivePath = pArchivePath;
    copyPath = pCachePath.resolve(archivePath.getFileName());
    if (pPack200)
      copyPath = PackUtility.getPackPath(copyPath);

    checkSumPath = pCachePath.resolve(copyPath.getFileName());
    signedCheckSumPath = pCachePath.resolve(copyPath.getFileName() + ".signed");

    if (pForceSign || !Files.exists(copyPath))
      type = TYPE.NEW;
    else
    {
      boolean signedExists = pSignChecksumHelper.existingChecksumMatches(checkSumPath, archivePath, pRepack);
      if (signedExists)
        type = TYPE.CACHED;
      else
      {
        boolean alreadySigned = pSignChecksumHelper.existingChecksumMatches(signedCheckSumPath, archivePath, pRepack);
        type = alreadySigned ? TYPE.SIGNED : TYPE.NEW;
      }
    }
  }

  public Path getArchivePath()
  {
    return archivePath;
  }

  public Path getCopyPath()
  {
    return copyPath;
  }

  public Path getCheckSumPath()
  {
    return checkSumPath;
  }

  public Path getSignedCheckSumPath()
  {
    return signedCheckSumPath;
  }

  public TYPE getType()
  {
    return type;
  }


  /**
   * Type enum
   */
  enum TYPE
  {
    NEW,
    CACHED,
    SIGNED
  }

}
