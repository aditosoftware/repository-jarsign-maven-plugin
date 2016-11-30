package de.adito.maven.repositoryjarsignplugin;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.logging.*;

/**
 * @author j.boesl, 30.11.16
 */
class MavenLogger extends AbstractLogger
{
  private Log log;

  MavenLogger(Log pLog)
  {
    super(1, "maven");
    log = pLog;
  }

  @Override
  public void debug(String pS, Throwable pThrowable)
  {
    log.debug(pS, pThrowable);
  }

  @Override
  public void info(String pS, Throwable pThrowable)
  {
    log.info(pS, pThrowable);
  }

  @Override
  public void warn(String pS, Throwable pThrowable)
  {
    log.warn(pS, pThrowable);
  }

  @Override
  public void error(String pS, Throwable pThrowable)
  {
    log.error(pS, pThrowable);
  }

  @Override
  public void fatalError(String pS, Throwable pThrowable)
  {
    log.error(pS, pThrowable);
  }

  @Override
  public Logger getChildLogger(String pS)
  {
    return this;
  }
}
