package de.adito.maven.repositoryjarsignplugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.digest.*;
import org.codehaus.plexus.util.FileUtils;

import java.io.IOException;
import java.nio.file.*;

/**
 * Helper for checksum.
 *
 * @author j.boesl, 27.08.15
 */
class SignChecksumHelper
{

  private Log log;
  private Digester digester;


  public SignChecksumHelper(Log pLog, Digester pDigester)
  {
    log = pLog;
    digester = pDigester;
  }

  boolean existingChecksumMatches(Path pChecksumPath, Path pArchivePath, boolean pRepack) throws MojoExecutionException
  {
    String existingChecksum = readChecksum(pChecksumPath);
    return existingChecksum != null && _calculateChecksum(pArchivePath, pRepack).equals(existingChecksum);
  }

  String readChecksum(Path pChecksumPath)
  {
    try
    {
      Path path = pChecksumPath.resolveSibling(pChecksumPath.getFileName() + _getPostfix());
      return FileUtils.fileRead(path.toFile());
    }
    catch (IOException e)
    {
      return null;
    }
  }

  void installSignChecksum(Path pChecksumPath, Path pArchivePath, boolean pRepack) throws MojoExecutionException
  {
    String checksum = _calculateChecksum(pArchivePath, pRepack);

    Path path = pChecksumPath.resolveSibling(pChecksumPath.getFileName() + _getPostfix());
    log.debug("Installing checksum to " + path);
    try
    {
      Files.createDirectories(path.getParent());
      FileUtils.fileWrite(path.toFile(), "UTF-8", checksum);
    }
    catch (IOException e)
    {
      throw new MojoExecutionException("Failed to install checksum to " + path, e);
    }
  }

  private String _calculateChecksum(Path pArchivePath, boolean pRepack) throws MojoExecutionException
  {
    log.debug("Calculating " + digester.getAlgorithm() + " checksum for " + pArchivePath);
    try
    {
      String checksum = digester.calc(pArchivePath.toFile());
      return (pRepack ? "REPACK" : "NO_REPACK") + ":" + checksum;
    }
    catch (DigesterException e)
    {
      throw new MojoExecutionException("Failed to calculate " + digester.getAlgorithm() + " checksum for " + pArchivePath, e);
    }
  }

  private String _getPostfix()
  {
    return "." + digester.getAlgorithm().toLowerCase();
  }

}
