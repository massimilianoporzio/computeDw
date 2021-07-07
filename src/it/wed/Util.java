package it.wed;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.Opener;
import ij.IJ;
import ij.measure.Measurements;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageStatistics;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

public class Util {

    public static String ExtractInfoFromTag(String HEADER, String TAG) {
        int STlen = 15; //gggg,eeee + " ---:"
        String INFO = "";
        // System.err.println("TAG: "+TAG);
        if (HEADER != null) {
            int index1 = HEADER.indexOf(TAG);

            int index2 = HEADER.indexOf(":", index1);
            int index3 = HEADER.indexOf("\n", index2);
            if (index1 >= 0 && index2 >= 0 && index3 >= 0) {
                INFO = HEADER.substring(index1 + STlen, index3);
                INFO = INFO.trim();
            }
        }
        return INFO;
    }

    public static String ExtractInfo(String HEADER, String ST1, String ST2) {
        int STlen = ST1.length();
        String INFO = "";
        if (HEADER != null) {
            int index1 = HEADER.indexOf(ST1);
            int index2 = HEADER.indexOf(ST2, index1);
            if (index1 >= 0 && index2 >= 0) {
                INFO = HEADER.substring(index1 + STlen, index2);
                INFO = INFO.trim();
            }
        }
        return INFO;
    }

    public static float getPixelDimensionFromHeader(ImagePlus imp) {
        float dimPx = 0.0f;

        String header = (String) imp.getInfoProperty();

        String HeadDimPix = ExtractInfo(header, "Pixel Spacing:", "\\");
        if (HeadDimPix == "") {
            //  dimPx=0.195f;}//eurocolumbus raw (1536x1536)
            dimPx = Float.NaN;
        }//eurocolumbus raw (1536x1536)
        else {
            dimPx = (float) (Double.parseDouble(HeadDimPix));
        }
        return dimPx;
    }

    public static double[] getWaterEquivalentInfoFromImbuto(ImagePlus imp){
        double[] results = new double[3];
        results[0] = Double.NaN;
        results[1] = Double.NaN; //MEDIA DIAMETRI BOUNDING BOX
        results[2] = Double.NaN; //DIAM TEORICO DA FORMULA USANDO DIAM DA BOUNDING BOX E HU medio
        // System.err.println("ARRAY A NAN");
        IJ.run("Set Measurements...", "area mean standard min perimeter bounding fit median redirect=None decimal=2");
        IJ.setThreshold(imp, -410, 32767);
        //System.err.println("SOGLIA IMPOSTATA");
        RoiManager rm = RoiManager.getInstance2();

        if (rm == null) {
            rm = new RoiManager(false);
        }
        if(rm==null){
            //  System.err.println("NO ROI MANAGER!");
        }
        // System.err.println("ROI MANAGER CREATO");
        ParticleAnalyzer.setRoiManager(rm);
        // System.err.println("ROI MANAGER IMPOSTATO"); //DA RIVEDERE PER TENERE CONTO DI TOTLA BODY ecc con ROI piccole
        IJ.run(imp, "Analyze Particles...", "size=5000-Infinity clear add ");
        // System.err.println("ANALISI PARTICELLE FATTA");

        Roi[] rois = rm.getRoisAsArray();

        if(rois==null || rois.length==0) {
            //System.err.println("INVERTO?");
            IJ.run(imp, "Invert LUT", "");
            IJ.setThreshold(imp, -410, 32767); //PROVARE CON -200 come ANAM
            IJ.run(imp, "Analyze Particles...", "size=5000-Infinity clear add");
            rois = rm.getRoisAsArray();

        }
        // System.err.println("TROVATO IL BODY?");
        //  System.err.println("rois.length: "+rois.length);
        if(rois==null || rois.length==0 || rois.length>1)
        {

            rm.close();
            //System.err.println("BODY NON TROVATO O PIU DI UNA ROI");
            return results; // ERRORE TROVATO PIU DI UN BODY O NON TROVATO
        }
        //System.err.println("RESET");
        rm.reset();
        //System.err.println("CLOSE");
        rm.close();
        rm = null;
        // System.err.println("MESSO A NULL");

        Roi roiBody = rois[0];
        imp.setRoi(roiBody);
        // System.err.println("ROI SETTATA");
        double dimPx = Util.getPixelDimensionFromHeader(imp);

        ImageStatistics stat = ImageStatistics.getStatistics(imp.getProcessor(), Measurements.ELLIPSE,imp.getCalibration());
        // System.err.println("STAT PRESE");
        double HU = stat.mean;
//        System.err.println(HU);
        double diametro = (roiBody.getBounds().height*dimPx+roiBody.getBounds().width*dimPx)/20;


        results[1] = diametro;
        double area =stat.area;
        double Aw = area*(0.001*HU+1);
        double Dw = 0.2*Math.sqrt(Aw/Math.PI);
        results[0] = Dw;
        return results;
    }

    public static void sortDirectoryByImageNumber(String dir){
        File workingDir = new File(dir);
        File[] listFile = workingDir.listFiles();

        int count=1;
        try{
            Files.createDirectories(Paths.get(dir+"/sorted"));
        }
        catch(IOException e){
            System.err.println(e.getMessage());
        }

        for (File f : listFile) {
            if (f.isDirectory())
            {
                continue;
            }
            //System.err.println("APRO IL FILE");
            ImagePlus img = new Opener().openImage(f.getPath());

            String header = (String) img.getProperty("Info");

            //chiudo le ImagePlus
            img.changes = false; //evito la richiesta di salvare prima di chiudere

            img.close();

            //se manca l'header ritorna 0 (non fa nessun compare)
            if (header == null) {
                IJ.error("error! Found a file with no DICOM header.");
                return;


            }

            String imageNumber = ExtractInfo(header, "Image Number:", "\n");
            if(imageNumber.isEmpty())
            {

                continue; // non è un file con il tag slice Location --skip
            }
            Path path = Paths.get(f.getAbsolutePath());


            try {
                Path FROM = path;
                Path TO = Paths.get(dir,"sorted",imageNumber);
                // System.err.println("TO: "+TO);
                //path.resolveSibling("/sorted/"+sliceLocation + ".dcm");
                //overwrite the destination file if it exists, and copy
                // the file attributes, including the rwx permissions
                CopyOption[] options = new CopyOption[]{

                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES
                };

                // Files.move(path, path.resolveSibling(sliceLocation + ".dcm"));

                Files.copy(FROM, TO, options);
            } catch (IOException ex) {

            }

        }
    }
}
