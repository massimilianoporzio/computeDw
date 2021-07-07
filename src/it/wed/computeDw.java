package it.wed;

import ij.ImagePlus;
import ij.io.Opener;
import ij.IJ;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class computeDw {

    public static void main(String[] args) {
        Instant start = Instant.now();
        //queryExample();
        //mutationExample();
        //VA SISTEMATA PER LE PERFOMANCES!!!
        File fileData = new File("E:\\DW\\dataPatient.txt");
        FileWriter fr = null;
        BufferedWriter br = null;

        try {
            for (String s : args) {
                System.out.println(s);
            }
            //IL PATH SARA' QUELLO CONTENENTE TUTTE LE SERIE
            //BISOGNA GIRARE PER OGNI DRECTORY E IN OGNUNA FARE IL SORTED
            //E FAR CALCOLARE DW e SSDE
            String pathString = args[0];

            Util.sortDirectoryByImageNumber(pathString);
            pathString+="/sorted"; //PASSO A LVAORARE NELLA DIRECTORY CREATA CON LE SLICE
            Path workingDir = Paths.get(pathString);
            System.err.println("workingDir: "+pathString);
            int numImmagini = 0;
            //List<Path> lista = new ArrayList<>();
            List<String> listaNomi = new ArrayList<>();
            try ( DirectoryStream<Path> stream = Files.newDirectoryStream(workingDir)) {

                for (Path file : stream) {

                    listaNomi.add(file.getFileName().toString());

                    numImmagini++;
                }
            } catch (IOException | DirectoryIteratorException x) {
                // IOException can never be thrown by the iteration.
                // In this snippet, it can only be thrown by newDirectoryStream.
                System.err.println(x);
            }

            //CREO COMPARATOR
            Comparator<String> compareBySliceLocation = new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    Double d1 = Double.parseDouble(o1);
                    Double d2 = Double.parseDouble(o2);
                    return d1.compareTo(d2);
                }
            };

            Collections.sort(listaNomi,compareBySliceLocation);
            compareBySliceLocation = null;


            Opener opener = new Opener();

            int numeroImmagineProcessate=0;

            //System.err.println("listaNomi: "+listaNomi);
            for(String nomeFile : listaNomi)
            {
                //System.err.println("PROVO A APRIRE IL FILE "+nomeFile);
                opener = new Opener();
                ImagePlus imp = opener.openImage(pathString, nomeFile);
                // System.err.println("APERTA");
                String header = (String) imp.getProperty("Info");
                String sCTDIvol = Util.ExtractInfoFromTag(header, "0018,9345");
                Double ctdiVol = Double.parseDouble(sCTDIvol);
                double[] waterEq = Util.getWaterEquivalentInfoFromImbuto(imp);
                if(Double.isNaN(waterEq[0])){
                    //NON C'ERA IL BODY O PIU DI UNA ROY
                    continue;
                }

                double ssde = ctdiVol*3.704369*Math.exp(-0.03671937*waterEq[0]);
                //System.err.println("CHECK");
                //SCRIVO SU FILE
                try {
                    // to append to file, you need to initialize FileWriter using below constructor
                    fr = new FileWriter(fileData, true);
                    br = new BufferedWriter(fr);
                    br.newLine();
                    String text = nomeFile+"\t"+IJ.d2s(waterEq[1],2)+"\t"+IJ.d2s(waterEq[0],2)+"\t"+IJ.d2s(ssde,2)+
                            "\t"+IJ.d2s(ctdiVol,1)+"\t"+IJ.d2s(waterEq[2],2);
                    br.write(text);


                } catch (IOException e) {
                    e.printStackTrace();

                } finally {
                    try {
                        br.close();
                        fr.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                numeroImmagineProcessate++;


            }//FINE GIRO SU SLICE
            //DIVIDO PER NUMERO FETTE PROCESSATE E HO LA MEDIA


            //SCRIVO SU DB CON MUTATION
            //FACCIO MUTATION DELLE SERIE DANDO GIA' L'ACCESSION NUMBER




            Instant finish = Instant.now();
            long timeElapsed = Duration.between(start, finish).toMillis();  //in millis
            System.err.println("DURATA PER PROCESSARE "+numImmagini+" IMMAGINI: "+timeElapsed +" ms");
        }//FINE TRY GENERALE DI TUTTO IL MAIN
        catch (Exception e) {
            System.err.println("ERRORE GENERALE");
            System.err.println(e.getMessage());
        }

    }

}
