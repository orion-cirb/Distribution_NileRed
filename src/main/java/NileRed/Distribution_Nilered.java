package NileRed;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import mcib3d.geom.Object3D;
import mcib3d.geom.Objects3DPopulation;
import org.apache.commons.io.FilenameUtils;


/**
 *
 * @author phm
 */
        
        
public class Distribution_Nilered implements PlugIn {
    
    private boolean canceled;
    private String imageDir;
    private static String outDirResults;
    public static Calibration cal = new Calibration();
    private NileRed_Tools tools = new NileRed_Tools();
    RoiManager rm;
    /**
     * 
     * @param arg
     */
    @Override
    public void run(String arg) {
        try {
            if (canceled) {
                IJ.showMessage(" Pluging canceled");
                return;
            }
            if (!tools.checkInstalledModules()) {
                IJ.showMessage(" Pluging canceled");
                return;
            }
            imageDir = IJ.getDirectory("Images folder");
            if (imageDir == null) {
                return;
            }
            File inDir = new File(imageDir);
            String fileExt = tools.findImageType(inDir);
            ArrayList<String> imageFiles = tools.findImages(imageDir, fileExt);
            if (imageFiles == null) {
                return;
            }
            
            // create output folder
            outDirResults = imageDir + "Results"+ File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
             // create rois folder
            String roiDirFold = imageDir + "Rois"+ File.separator;
            File roiDir = new File(roiDirFold);
            if (!Files.exists(Paths.get(roiDirFold))) {
                roiDir.mkdir();
            }

            tools.setImageCalib();
            String chanel = tools.dialog();
            
            // write headers for results tables
            tools.writeHeaders(outDirResults);
            File dir = new File(imageDir);
            
            // Draw Rois if not already done
            rm = RoiManager.getInstance();
            if ( rm == null ) rm = new RoiManager(false);
            //rm.reset();
            if (rm.getCount()>0) {
                rm.runCommand("Deselect");
                rm.runCommand("Delete");
            }
            for (String f : dir.list()) {
                
                if (f.contains(chanel)){
                     String rootName = FilenameUtils.getBaseName(f);
                     String filename = rootName+".TIF";
                     if (!Files.exists(Paths.get(roiDirFold+rootName+".zip"))) {
                         ImagePlus img = IJ.openImage(imageDir+filename);
                         img.show();
                         IJ.setTool("oval");
                         new WaitForUserDialog("Draw oocyte contour on middle slice").show();
                         Roi roi = img.getRoi();
                         roi.setPosition(img.getSlice());
                         roi.setImage(img);
                         if (rm.getCount()>0) {
                            rm.runCommand("Deselect");
                            rm.runCommand("Delete");
                         }
                         rm.addRoi(roi);
                         rm.runCommand("Save", roiDirFold+rootName+".zip");
                         img.hide();
                         tools.closeImages(img);
                    }
                }
            }
            
            // Work on the files
            for (String f : dir.list()) {
                
                if (f.contains(chanel)){
                     String rootName = FilenameUtils.getBaseName(f);
                     String filename = rootName+".TIF";
               
                     // open NileRed channel 
                    ImagePlus img = IJ.openImage(imageDir+filename);
                    tools.setCalibration(img);
                
                // get oocyte contour
                 if (rm.getCount()>0) {
                    rm.runCommand("Deselect");
                    rm.runCommand("Delete");
                }     
                rm.runCommand("Open", roiDirFold+rootName+".zip");
                Roi roi = rm.getRoisAsArray()[0];
                //System.out.println(roi.getPosition());
                tools.getSphereFromRoi(roi);   
                
                // get Dots
                Objects3DPopulation dotPop = tools.findDotsDoG(img);
                System.out.println(dotPop.getNbObjects()+" dots found");
                tools.saveDotsImage(dotPop, img, outDirResults+rootName+"_Dots.tif");
                                        
                // find parameters
                tools.countSpots(dotPop, img, filename, outDirResults);
                tools.closeImages(img);
                }
                
            }
            tools.closeResults();
            IJ.showStatus("Processing done....");
        } catch (Exception ex) {
            Logger.getLogger(Distribution_Nilered.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

