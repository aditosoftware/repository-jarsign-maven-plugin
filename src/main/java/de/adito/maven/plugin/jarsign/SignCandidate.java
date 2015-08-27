package de.adito.maven.plugin.jarsign;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.nio.file.Path;

/**
 * @author j.boesl, 27.08.15
 */
public class SignCandidate
{

  private File file;
  private File workingCopy;
  private String checkSumPath;
  private String signedCheckSumPath;
  private TYPE type;

  public SignCandidate(File pFile, Path pCachePath, SignChecksumHelper pSignChecksumHelper, boolean pForceSign) throws MojoExecutionException
  {
    file = pFile;
    workingCopy = new File(pCachePath.toFile(), file.getName());

    checkSumPath = pCachePath.resolve(file.getName()).toFile().getAbsolutePath();
    signedCheckSumPath = pCachePath.resolve(file.getName() + ".signed").toFile().getAbsolutePath();

    if (pForceSign)
      type = TYPE.NEW;
    else
    {
      boolean signedExists = pSignChecksumHelper.existingChecksumMatches(file, checkSumPath);
      if (signedExists)
        type = TYPE.CACHED;
      else
      {
        boolean alreadySigned = pSignChecksumHelper.existingChecksumMatches(file, signedCheckSumPath);
        type = alreadySigned ? TYPE.SIGNED : TYPE.NEW;
      }
    }
  }

  public File getFile()
  {
    return file;
  }

  public File getWorkingCopy()
  {
    return workingCopy;
  }

  public String getCheckSumPath()
  {
    return checkSumPath;
  }

  public String getSignedCheckSumPath()
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
