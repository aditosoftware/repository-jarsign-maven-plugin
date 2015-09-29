package de.adito.maven.plugin.jarsign;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.jar.*;

/**
 * Utility for pack200
 *
 * @author j.boesl, 29.09.15
 */
public class PackUtility
{

  private static final String PACK_POSTFIX = ".pack.gz";


  private PackUtility()
  {
  }


  static Path getPackPath(Path pArchivePath)
  {
    return pArchivePath.resolveSibling(pArchivePath.getFileName() + PACK_POSTFIX);
  }

  static Path pack(Path pArchivePath) throws IOException
  {
    Path packPath = getPackPath(pArchivePath);
    pack(pArchivePath, packPath);
    return packPath;
  }

  static void pack(Path pSource, Path pTarget) throws IOException
  {
    Pack200.Packer packer = _getPacker();
    try (JarInputStream inputStream = new JarInputStream(Files.newInputStream(pSource));
         OutputStream outputStream = Files.newOutputStream(pTarget))
    {
      packer.pack(inputStream, outputStream);
    }
  }

  static void unpack(Path pPackPath, Path pArchivePath) throws IOException
  {
    Pack200.Unpacker unpacker = Pack200.newUnpacker();
    try (InputStream inputStream = Files.newInputStream(pPackPath);
         JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(pArchivePath)))
    {
      unpacker.unpack(inputStream, outputStream);
    }
  }

  static private Pack200.Packer _getPacker()
  {
    Pack200.Packer packer = Pack200.newPacker();

    // Initialize the state by setting the desired properties
    Map<String, String> p = packer.properties();
    // take more time choosing codings for better compression
    p.put(Pack200.Packer.EFFORT, "7");  // default is "5"
    // use largest-possible archive segments (>10% better compression).
    p.put(Pack200.Packer.SEGMENT_LIMIT, "-1");
    // reorder files for better compression.
    p.put(Pack200.Packer.KEEP_FILE_ORDER, Pack200.Packer.FALSE);
    // smear modification times to a single value.
    p.put(Pack200.Packer.MODIFICATION_TIME, Pack200.Packer.LATEST);
    // ignore all JAR deflation requests,
    // transmitting a single request to use "store" mode.
    p.put(Pack200.Packer.DEFLATE_HINT, Pack200.Packer.FALSE);
    // throw an error if an attribute is unrecognized
    p.put(Pack200.Packer.UNKNOWN_ATTRIBUTE, Pack200.Packer.ERROR);

    return packer;
  }

}
