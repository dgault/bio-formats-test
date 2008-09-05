//
// FormatReaderTest.java
//

/*
LOCI software automated test suite for TestNG. Copyright (C) 2007-@year@
Melissa Linkert and Curtis Rueden. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
  * Neither the name of the UW-Madison LOCI nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE UW-MADISON LOCI ``AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package loci.tests.testng;

import java.awt.image.BufferedImage;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import loci.formats.*;
import loci.formats.in.*;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.meta.MetadataStore;
import org.testng.SkipException;

/**
 * TestNG tester for Bio-Formats file format readers.
 * Details on failed tests are written to a log file, for easier processing.
 *
 * NB: {@link loci.formats.ome} and ome-java.jar
 * are required for some of the tests.
 *
 * To run tests:
 * ant -Dtestng.directory="/path" -Dtestng.multiplier="1.0" test-all
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/test-suite/src/loci/tests/testng/FormatReaderTest.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/test-suite/src/loci/tests/testng/FormatReaderTest.java">SVN</a></dd></dl>
 */
public class FormatReaderTest {

  // -- Constants --

  /** Message to give for why a test was skipped. */
  private static final String SKIP_MESSAGE = "Dataset already tested.";

  // -- Static fields --

  /** Configuration tree structure containing dataset metadata. */
  public static ConfigurationTree config;

  /** List of files to skip. */
  public static List skipFiles = new LinkedList();

  /** Global shared reader for use in all tests. */
  private static IFormatReader reader;

  // -- Fields --

  private String id;
  private boolean skip = false;

  /**
   * Multiplier for use adjusting timing values. Slower machines take longer to
   * complete the timing test, and thus need to set a higher (&gt;1) multiplier
   * to avoid triggering false timing test failures. Conversely, faster
   * machines should set a lower (&lt;1) multipler to ensure things finish as
   * quickly as expected.
   */
  private float timeMultiplier = 1;

  // -- Constructor --

  public FormatReaderTest(String filename, float multiplier) {
    id = filename;
    timeMultiplier = multiplier;
  }

  // -- Tests --

  /**
   * @testng.test groups = "all pixels"
   */
  public void testBufferedImageDimensions() {
    if (!initFile()) return;
    String file = reader.getCurrentFile();
    String testName = "testBufferedImageDimensions";
    boolean success = true;
    String msg = null;
    try {
      BufferedImage b = null;
      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);

        int x = reader.getSizeX();
        int y = reader.getSizeY();
        int c = reader.getRGBChannelCount();
        int type = reader.getPixelType();

        int num = reader.getImageCount();
        if (num > 3) num = 3; // test first three image planes only, for speed
        for (int j=0; j<num && success; j++) {
          b = reader.openImage(j);

          int actualX = b.getWidth();
          boolean passX = x == actualX;
          if (!passX) msg = "X: was " + actualX + ", expected " + x;

          int actualY = b.getHeight();
          boolean passY = y == actualY;
          if (!passY) msg = "Y: was " + actualY + ", expected " + y;

          int actualC = b.getRaster().getNumBands();
          boolean passC = c == actualC;
          if (!passC) msg = "C: was " + actualC + ", expected " + c;

          int actualType = ImageTools.getPixelType(b);
          boolean passType = type == actualType;
          if (!passType && actualType == FormatTools.UINT16 &&
            type == FormatTools.INT16)
          {
            passType = true;
          }

          if (!passType) msg = "type: was " + actualType + ", expected " + type;

          success = passX && passY && passC && passType;
        }
      }
    }
    catch (Throwable t) {
      LogTools.trace(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test groups = "all pixels"
   */
  public void testByteArrayDimensions() {
    if (!initFile()) return;
    String file = reader.getCurrentFile();
    String testName = "testByteArrayDimensions";
    boolean success = true;
    String msg = null;
    try {
      byte[] b = null;
      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);
        int x = reader.getSizeX();
        int y = reader.getSizeY();
        int c = reader.isIndexed() ? 1 : reader.getRGBChannelCount();
        int bytes = FormatTools.getBytesPerPixel(reader.getPixelType());

        int expected = x * y * c * bytes;

        int num = reader.getImageCount();
        if (num > 3) num = 3; // test first three planes only, for speed
        for (int j=0; j<num && success; j++) {
          b = reader.openBytes(j);
          success = b.length == expected;
          if (!success) {
            msg = "series #" + i + ", image #" + j +
              ": was " + b.length + ", expected " + expected;
          }
        }
      }
    }
    catch (Throwable t) {
      LogTools.trace(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test groups = "all pixels"
   */
  public void testThumbnailImageDimensions() {
    if (!initFile()) return;
    String file = reader.getCurrentFile();
    String testName = "testThumbnailImageDimensions";
    boolean success = true;
    String msg = null;
    try {
      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);

        int x = reader.getThumbSizeX();
        int y = reader.getThumbSizeY();
        int c = reader.getRGBChannelCount();
        int type = reader.getPixelType();

        BufferedImage b = reader.openThumbImage(0);

        int actualX = b.getWidth();
        boolean passX = x == actualX;
        if (!passX) {
          msg = "series #" + i + ": X: was " + actualX + ", expected " + x;
        }

        int actualY = b.getHeight();
        boolean passY = y == actualY;
        if (!passY) {
          msg = "series #" + i + ": Y: was " + actualY + ", expected " + y;
        }

        int actualC = b.getRaster().getNumBands();
        boolean passC = c == actualC;
        if (!passC) {
          msg = "series #" + i + ": C: was " + actualC + ", expected < " + c;
        }

        int actualType = ImageTools.getPixelType(b);
        boolean passType = type == actualType;
        if (!passType && actualType == FormatTools.UINT16 &&
          type == FormatTools.INT16)
        {
          passType = true;
        }

        if (!passType) {
          msg = "series #" + i + ": type: was " +
            actualType + ", expected " + type;
        }

        success = passX && passY && passC && passType;
      }
    }
    catch (Throwable t) {
      LogTools.trace(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test groups = "all pixels"
   */
  public void testThumbnailByteArrayDimensions() {
    if (!initFile()) return;
    String file = reader.getCurrentFile();
    String testName = "testThumbnailByteArrayDimensions";
    boolean success = true;
    String msg = null;
    try {
      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);
        int x = reader.getThumbSizeX();
        int y = reader.getThumbSizeY();
        int c = reader.isIndexed() ? 1 : reader.getRGBChannelCount();
        int bytes = FormatTools.getBytesPerPixel(reader.getPixelType());

        int expected = x * y * c * bytes;

        byte[] b = reader.openThumbBytes(0);
        success = b.length == expected;
        if (!success) {
          msg = "series #" + i + ": was " + b.length + ", expected " + expected;
        }
      }
    }
    catch (Throwable t) {
      LogTools.trace(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test groups = "all fast"
   */
  public void testImageCount() {
    if (!initFile()) return;
    String file = reader.getCurrentFile();
    String testName = "testImageCount";
    boolean success = true;
    String msg = null;
    try {
      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);
        int imageCount = reader.getImageCount();
        int z = reader.getSizeZ();
        int c = reader.getEffectiveSizeC();
        int t = reader.getSizeT();
        success = imageCount == z * c * t;
        msg = "series #" + i + ": imageCount=" + imageCount +
          ", z=" + z + ", c=" + c + ", t=" + t;
      }
    }
    catch (Throwable t) {
      LogTools.trace(t);
      success = false;
    }
    result(testName, success);
  }

  /**
   * @testng.test groups = "all xml fast"
   */
  public void testOMEXML() {
    if (!initFile()) return;
    String file = reader.getCurrentFile();
    String testName = "testOMEXML";
    boolean success = true;
    String msg = null;
    try {
      MetadataRetrieve retrieve = (MetadataRetrieve) reader.getMetadataStore();
      success = MetadataTools.isOMEXMLMetadata(retrieve);
      if (!success) msg = shortClassName(retrieve);

      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);

        String type = FormatTools.getPixelTypeString(reader.getPixelType());

        boolean passX = reader.getSizeX() ==
          retrieve.getPixelsSizeX(i, 0).intValue();
        if (!passX) msg = "SizeX";
        boolean passY = reader.getSizeY() ==
          retrieve.getPixelsSizeY(i, 0).intValue();
        if (!passY) msg = "SizeY";
        boolean passZ = reader.getSizeZ() ==
          retrieve.getPixelsSizeZ(i, 0).intValue();
        if (!passZ) msg = "SizeZ";
        boolean passC = reader.getSizeC() ==
          retrieve.getPixelsSizeC(i, 0).intValue();
        if (!passC) msg = "SizeC";
        boolean passT = reader.getSizeT() ==
          retrieve.getPixelsSizeT(i, 0).intValue();
        if (!passT) msg = "SizeT";
        boolean passBE = reader.isLittleEndian() !=
          retrieve.getPixelsBigEndian(i, 0).booleanValue();
        if (!passBE) msg = "BigEndian";
        boolean passDE = reader.getDimensionOrder().equals(
          retrieve.getPixelsDimensionOrder(i, 0));
        if (!passDE) msg = "DimensionOrder";
        boolean passType = type.equalsIgnoreCase(
          retrieve.getPixelsPixelType(i, 0));
        if (!passType) msg = "PixelType";

        success = passX && passY && passZ &&
          passC && passT && passBE && passDE && passType;
      }
    }
    catch (Throwable t) {
      LogTools.trace(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test groups = "all fast"
   */
  public void testConsistent() {
    if (!initFile()) return;
    if (config == null) throw new SkipException("No config tree");
    String file = reader.getCurrentFile();
    String testName = "testConsistent";
    boolean success = true;
    String msg = null;
    try {
      int numSeries = config.getNumSeries();
      if (numSeries == 0) {
        success = false;
        msg = "no configuration";
      }
      else if (reader.getSeriesCount() != numSeries) {
        success = false;
        msg = "series counts differ: was " +
          reader.getSeriesCount() + ", expected " + numSeries;
      }

      for (int i=0; i<numSeries && success; i++) {
        reader.setSeries(i);
        config.setSeries(i);

        int actualX = reader.getSizeX();
        int expectedX = config.getX();
        boolean passX = actualX == expectedX;
        if (!passX) msg = "SizeX: was " + actualX + ", expected " + expectedX;

        int actualY = reader.getSizeY();
        int expectedY = config.getY();
        boolean passY = actualY == expectedY;
        if (!passY) msg = "SizeY: was " + actualY + ", expected " + expectedY;

        int actualZ = reader.getSizeZ();
        int expectedZ = config.getZ();
        boolean passZ = actualZ == expectedZ;
        if (!passZ) msg = "SizeZ: was " + actualZ + ", expected " + expectedZ;

        int actualC = reader.getSizeC();
        int expectedC = config.getC();
        boolean passC = actualC == expectedC;
        if (!passC) msg = "SizeC: was " + actualC + ", expected " + expectedC;

        int actualT = reader.getSizeT();
        int expectedT = config.getT();
        boolean passT = actualT == expectedT;
        if (!passT) msg = "SizeT: was " + actualT + ", expected " + expectedT;

        String actualDim = reader.getDimensionOrder();
        String expectedDim = config.getOrder();
        boolean passDim = expectedDim.equals(actualDim);
        if (!passDim) {
          msg = "DimensionOrder: was " + actualDim +
            ", expected " + expectedDim;
        }

        boolean actualInt = reader.isInterleaved();
        boolean expectedInt = config.isInterleaved();
        boolean passInt = actualInt == expectedInt;
        if (!passInt) {
          msg = "interleaved: was " + actualInt + ", expected " + expectedInt;
        }

        boolean actualRGB = reader.isRGB();
        boolean expectedRGB = config.isRGB();
        boolean passRGB = actualRGB == expectedRGB;
        if (!passRGB) {
          msg = "RGB: was " + actualRGB + ", expected " + expectedRGB;
        }

        int actualTX = reader.getThumbSizeX();
        int expectedTX = config.getThumbX();
        boolean passTX = actualTX == expectedTX;
        if (!passTX) {
          msg = "ThumbSizeX: was " + actualTX + ", expected " + expectedTX;
        }

        int actualTY = reader.getThumbSizeY();
        int expectedTY = config.getThumbY();
        boolean passTY = actualTY == expectedTY;
        if (!passTY) {
          msg = "ThumbSizeY: was " + actualTY + ", expected " + expectedTY;
        }

        int actualType = reader.getPixelType();
        int expectedType = config.getPixelType();
        boolean passType = actualType == expectedType;
        if (!passType) {
          msg = "PixelType: was " + actualType + ", expected " + expectedType;
        }

        boolean actualLE = reader.isLittleEndian();
        boolean expectedLE = config.isLittleEndian();
        boolean passLE = actualLE == expectedLE;
        if (!passLE) {
          msg = "little-endian: was " + actualLE + ", expected " + expectedLE;
        }

        boolean passIndexed = config.isIndexed() == reader.isIndexed();
        if (!passIndexed) {
          msg = "Indexed: was " + reader.isIndexed() + ", expected " +
            config.isIndexed();
        }

        boolean passFalseColor =
          config.isFalseColor() == reader.isFalseColor();

        if (!passFalseColor) {
          msg = "FalseColor: was " + reader.isFalseColor() + ", expected " +
            config.isFalseColor();
        }

        success = passX && passY && passZ && passC && passT && passDim &&
          passInt && passRGB && passTX && passTY && passType && passLE &&
          passIndexed && passFalseColor;
      }
    }
    catch (Throwable t) {
      LogTools.trace(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test groups = "all"
   */
  public void testPerformance() {
    if (!initFile()) return;
    if (config == null) throw new SkipException("No config tree");
    String file = reader.getCurrentFile();
    String testName = "testPerformance";
    boolean success = true;
    String msg = null;
    try {
      int properMem = config.getMemoryUse();
      double properTime = config.getTimePerPlane();
      if (properMem == 0 || properTime == 0) {
        success = false;
        msg = "no configuration";
      }
      else {
        Runtime r = Runtime.getRuntime();
        System.gc(); // clean memory before we start
        long m1 = r.totalMemory() - r.freeMemory();
        long t1 = System.currentTimeMillis();
        int totalPlanes = 0;
        int seriesCount = reader.getSeriesCount();
        for (int i=0; i<seriesCount; i++) {
          reader.setSeries(i);
          int imageCount = reader.getImageCount();
          totalPlanes += imageCount;
          for (int j=0; j<imageCount; j++) reader.openImage(j);
        }
        long t2 = System.currentTimeMillis();
        long m2 = r.totalMemory() - r.freeMemory();
        double actualTime = (double) (t2 - t1) / totalPlanes;
        int actualMem = (int) ((m2 - m1) >> 20);

        // check time elapsed
        if (actualTime - timeMultiplier * properTime > 20.0) {
          success = false;
          msg = "got " + actualTime + " ms, expected " + properTime + " ms";
        }

        // check memory used
        else if (actualMem > properMem) {
          success = false;
          msg =  "used " + actualMem + " MB; expected <= " + properMem + " MB";
        }
      }
    }
    catch (Throwable t) {
      LogTools.trace(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test groups = "all"
   */
  public void testSaneUsedFiles() {
    if (!initFile()) return;
    String file = reader.getCurrentFile();
    String testName = "testSaneUsedFiles";
    boolean success = true;
    String msg = null;
    try {
      String[] base = reader.getUsedFiles();
      if (base.length == 1) {
        if (!base[0].equals(file)) success = false;
      }
      else {
        Arrays.sort(base);
        IFormatReader r = new FileStitcher();
        for (int i=0; i<base.length && success; i++) {
          r.setId(base[i]);
          String[] comp = r.getUsedFiles();
          if (comp.length != base.length) {
            success = false;
            msg = base[i];
          }
          if (success) Arrays.sort(comp);
          for (int j=0; j<comp.length && success; j++) {
            if (!comp[j].equals(base[j])) {
              success = false;
              msg = base[i];
            }
          }
        }
      }
    }
    catch (Throwable t) {
      LogTools.trace(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test groups = "all xml fast"
   */
  public void testValidXML() {
    if (!initFile()) return;
    String file = reader.getCurrentFile();
    String testName = "testValidXML";
    boolean success = true;
    try {
      MetadataRetrieve retrieve = (MetadataRetrieve) reader.getMetadataStore();

      String xml = MetadataTools.getOMEXML(retrieve);
      success = xml != null;
      // TODO call XMLTools.validateXML; somehow get a return value
    }
    catch (Throwable t) {
      LogTools.trace(t);
      success = false;
    }
    result(testName, success);
  }

  /**
   * @testng.test groups = "all pixels"
   */
  public void testPixelsHashes() {
    if (!initFile()) return;
    if (config == null) throw new SkipException("No config tree");
    String file = reader.getCurrentFile();
    String testName = "testPixelsHashes";
    boolean success = true;
    String msg = null;
    try {
      // check the MD5 of the first plane in each series
      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);
        config.setSeries(i);

        String md5 = md5(reader.openBytes(0));
        String expected = config.getMD5();

        if (!md5.equals(expected)) {
          success = false;
          msg = expected == null ? "no configuration" : "series " + i;
        }
      }
    }
    catch (Throwable t) {
      LogTools.trace(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test groups = "all fast"
   */
  public void testIsThisType() {
    if (!initFile()) return;
    String file = reader.getCurrentFile();
    String testName = "testIsThisType";
    boolean success = true;
    String msg = null;
    try {
      IFormatReader r = reader;
      // unwrap reader
      while (true) {
        if (r instanceof ReaderWrapper) {
          r = ((ReaderWrapper) r).getReader();
        }
        else if (r instanceof FileStitcher) {
          r = ((FileStitcher) r).getReader();
        }
        else break;
      }
      if (r instanceof ImageReader) {
        ImageReader ir = (ImageReader) r;
        r = ir.getReader();
        IFormatReader[] readers = ir.getReaders();
        String[] used = reader.getUsedFiles();
        for (int i=0; i<used.length && success; i++) {
          // for each used file, make sure that one reader,
          // and only one reader, identifies the dataset as its own
          for (int j=0; j<readers.length; j++) {
            boolean result = readers[j].isThisType(used[i]);

            // TIFF reader is allowed to redundantly green-light files
            if (result && readers[j] instanceof TiffReader) continue;

            // Bio-Rad reader is allowed to redundantly
            // green-light PIC files from NRRD datasets
            if (result && r instanceof NRRDReader &&
              readers[j] instanceof BioRadReader)
            {
              String low = used[i].toLowerCase();
              boolean isPic = low.endsWith(".pic") || low.endsWith(".pic.gz");
              if (isPic) continue;
            }

            boolean expected = r == readers[j];
            if (result != expected) {
              success = false;
              if (result) {
                msg = shortClassName(readers[j]) + " flagged \"" +
                  used[i] + "\" but so did " + shortClassName(r);
              }
              else {
                msg = shortClassName(readers[j]) +
                  " skipped \"" + used[i] + "\"";
              }
              break;
            }
          }
        }
      }
      else {
        success = false;
        msg = "Reader " + r.getClass().getName() + " is not an ImageReader";
      }
    }
    catch (Throwable t) {
      LogTools.trace(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test groups = "config"
   */
  public void writeConfigFile() {
    if (!initFile()) return;
    String file = reader.getCurrentFile();
    LogTools.println("Generating configuration: " + file);
    Exception exc = null;
    try {
      StringBuffer line = new StringBuffer();
      line.append("\"");
      line.append(new Location(file).getName());
      line.append("\" total_series=");
      line.append(reader.getSeriesCount());
      int seriesCount = reader.getSeriesCount();
      for (int i=0; i<seriesCount; i++) {
        reader.setSeries(i);
        line.append(" [series=");
        line.append(i);
        line.append(" x=" + reader.getSizeX());
        line.append(" y=" + reader.getSizeY());
        line.append(" z=" + reader.getSizeZ());
        line.append(" c=" + reader.getSizeC());
        line.append(" t=" + reader.getSizeT());
        line.append(" order=" + reader.getDimensionOrder());
        line.append(" interleave=" + reader.isInterleaved());
        line.append(" rgb=" + reader.isRGB());
        line.append(" thumbx=" + reader.getThumbSizeX());
        line.append(" thumby=" + reader.getThumbSizeY());
        line.append(" type=" +
          FormatTools.getPixelTypeString(reader.getPixelType()));
        line.append(" little=" + reader.isLittleEndian());
        line.append(" indexed=" + reader.isIndexed());
        line.append(" falseColor=" + reader.isFalseColor());
        line.append(" md5=" + md5(reader.openBytes(0)));
        line.append("]");
      }

      // evaluate performance
      Runtime r = Runtime.getRuntime();
      System.gc(); // clean memory before we start
      long m1 = r.totalMemory() - r.freeMemory();
      long t1 = System.currentTimeMillis();
      int totalPlanes = 0;
      for (int i=0; i<seriesCount; i++) {
        reader.setSeries(i);
        int imageCount = reader.getImageCount();
        totalPlanes += imageCount;
        for (int j=0; j<imageCount; j++) reader.openImage(j);
      }
      long t2 = System.currentTimeMillis();
      long m2 = r.totalMemory() - r.freeMemory();
      double actualTime = (double) (t2 - t1) / totalPlanes;
      int actualMem = (int) ((m2 - m1) >> 20);

      line.append(" access=");
      line.append(actualTime);
      line.append(" mem=");
      line.append(actualMem);
      line.append(" test=true\n");

      File f = new File(new Location(file).getParent(), ".bioformats");
      BufferedWriter w = new BufferedWriter(new FileWriter(f, true));
      w.write(line.toString());
      w.close();
    }
    catch (Throwable t) {
      try {
        File f = new File(new Location(file).getParent(), ".bioformats");
        BufferedWriter w = new BufferedWriter(new FileWriter(f, true));
        w.write("\"" + file + "\" test=false\n");
        w.close();
      }
      catch (Throwable t2) {
        LogTools.trace(t2);
        assert false;
      }
    }
  }

  // -- Helper methods --

  /** Initializes the reader and configuration tree. */
  private boolean initFile() {
    if (skip) throw new SkipException(SKIP_MESSAGE);
    if (reader == null) {
      reader = new FileStitcher();
      reader.setNormalized(true);
      reader.setOriginalMetadataPopulated(true);
      reader.setMetadataFiltered(true);
      MetadataStore store = MetadataTools.createOMEXMLMetadata();
      reader.setMetadataStore(store);
    }
    if (id.equals(reader.getCurrentFile())) return true; // already initialized

    // skip files that were already tested as part of another file's dataset
    int ndx = skipFiles.indexOf(id);
    if (ndx >= 0) {
      LogTools.println("Skipping " + id);
      skipFiles.remove(ndx);
      skip = true;
      throw new SkipException(SKIP_MESSAGE);
    }

    LogTools.print(timestamp() + "Initializing " + id + ": ");
    try {
      reader.setId(id);
      // remove used files
      String[] used = reader.getUsedFiles();
      boolean base = false;
      for (int i=0; i<used.length; i++) {
        if (id.equals(used[i])) {
          base = true;
          continue;
        }
        skipFiles.add(used[i]);
      }
      boolean single = used.length == 1;
      if (single && base) LogTools.println("OK");
      else LogTools.println(used.length + (single ? " file" : " files"));
      if (!base) {
        LogTools.println("Error: used files list does not include base file");
      }

      // initialize configuration tree
      if (config != null) config.setId(id);
    }
    catch (Throwable t) {
      LogTools.println("error");
      LogTools.trace(t);
      return false;
    }
    return true;
  }

  /** Calculate the MD5 of a byte array. */
  private static String md5(byte[] b) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      md.reset();
      md.update(b);
      byte[] digest = md.digest();
      StringBuffer sb = new StringBuffer();
      for (int i=0; i<digest.length; i++) {
        String a = Integer.toHexString(0xff & digest[i]);
        if (a.length() == 1) a = "0" + a;
        sb.append(a);
      }
      return sb.toString();
    }
    catch (NoSuchAlgorithmException e) { LogTools.trace(e); }
    return null;
  }

  /** Outputs test result and generates appropriate assertion. */
  private static void result(String testName, boolean success) {
    result(testName, success, null);
  }

  /**
   * Outputs test result with optional extra message
   * and generates appropriate assertion.
   */
  private static void result(String testName, boolean success, String msg) {
    LogTools.println("\t" + timestamp() + ": " + testName + ": " +
      (success ? "PASSED" : "FAILED") + (msg == null ? "" : " (" + msg + ")"));
    if (msg == null) assert success;
    else assert success : msg;
  }

  /** Gets the class name sans package for the given object. */
  private static String shortClassName(Object o) {
    String name = o.getClass().getName();
    int dot = name.lastIndexOf(".");
    return dot < 0 ? name : name.substring(dot + 1);
  }

  /** Gets a timestamp for the current moment. */
  private static String timestamp() {
    return DataTools.convertDate(System.currentTimeMillis(), DataTools.UNIX);
  }

}
