package NileRed;


import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.process.AutoThresholder;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import javax.swing.ImageIcon;
import mcib3d.geom.Object3D;
import mcib3d.geom.Object3D_IJUtils;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;

        
 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose dots_Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author phm
 */
public class NileRed_Tools {
   
    // min volume in µm^3 for cells
    public double minDots = 0.5;
    // max volume in µm^3 for cells
    public double maxDots = 1000;
    // sigmas for DoG
    private double sigma1 = 0.5;
    private double sigma2 = 3;
    private double sigmaz = 1;
    // Dog parameters
    private Calibration cal = new Calibration(); 

    //private double radiusNei = 50; //neighboring radius
    //private int nbNei = 10; // K neighborg
        
    private BufferedWriter outPutResults;
    private BufferedWriter outPutDistances;
    
    // dots threshold method
    private String thMet = "Moments";
    
    private double[] cent = {0,0};  // cent is in pixels
    private double zcent = 0;       // zcent in slice number
    private double rad = 0;         // rad in pixels
    
    public CLIJ2 clij2 = CLIJ2.getInstance();
    public final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    
     /**
     * check  installed modules
     * @return 
     */
    public boolean checkInstalledModules() {
        // check install
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("mcib3d.geom.Object3D");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        
        return true;
    }
    
    /**
     * Find images in folder
     */
    public ArrayList<String> findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No Image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
      /**
     * Find image type
     */
    public String findImageType(File imagesFolder) {
        String ext = "";
        String[] files = imagesFolder.list();
        for (String name : files) {
            String fileExt = FilenameUtils.getExtension(name);
            switch (fileExt) {
                case "nd" :
                    return fileExt;
                case "czi" :
                  return fileExt;
                case "lif"  :
                    return fileExt;
                case "isc2" :
                   return fileExt;
                default :
                   ext = fileExt;
                   break; 
            }
        }
        return(ext);
    }
    
    /**
     * Set image calibration
     * @return 
     */
    public void setImageCalib() {
        cal = new Calibration();  
        // read image calibration
        cal.pixelWidth = 0.1135;
        cal.pixelHeight = cal.pixelWidth;
        cal.pixelDepth = 1;
        cal.setUnit("microns");
    }
    
    public void setCalibration(ImagePlus imp){
        imp.setCalibration(cal);
    }
    
    public Calibration getCalib()
    {
        System.out.println("x cal = " +cal.pixelWidth+", z cal=" + cal.pixelDepth);
        return cal;
    }
    
    public double getSD(double[] tab, double mean){
        double res = 0;
        for (int i=0; i<tab.length; i++){
            res += (tab[i]-mean)*(tab[i]-mean);
        }
        return Math.sqrt(res/tab.length);
    }
    
    /**
     *
     * @param img
     */
    public void closeImages(ImagePlus img) {
        img.flush();
        img.close();
    }
    
    public Objects3DPopulation getPopFromImage(ImagePlus img) {
        // label binary images first
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        Objects3DPopulation pop = new Objects3DPopulation(labels);
        return pop;
    }
      
    
    
    /* Median filter 
     * Using CLIJ2
     * @param ClearCLBuffer
     * @param sizeXY
     * @param sizeZ
     */ 
    public ClearCLBuffer median_filter(ClearCLBuffer  imgCL, double sizeXY, double sizeZ) {
        ClearCLBuffer imgCLMed = clij2.create(imgCL);
        clij2.median3DBox(imgCL, imgCLMed, sizeXY, sizeXY, sizeZ);
        clij2.release(imgCL);
        return(imgCLMed);
    }
    
    
    /**
     * Difference of Gaussians 
     * Using CLIJ2
     * @param imgCL
     * @param size1
     * @param size2
     * @return imgGauss
     */ 
    public ClearCLBuffer DOG(ClearCLBuffer imgCL, double size1, double size2, double sizez) {
        ClearCLBuffer imgCLDOG = clij2.create(imgCL);
        ClearCLBuffer imgCLDOGmed = median_filter(imgCLDOG, 0.5, 0.5);
        clij2.release(imgCLDOG);
        clij2.differenceOfGaussian3D(imgCL, imgCLDOGmed, size1, size1, sizez, size2, size2, sizez*size2/size1);
        clij2.release(imgCL);
        return(imgCLDOGmed);
    }
   
    
    /**
     * Dialog 
     * 
     * @param channels
     * @return 
     */
    public String dialog() {
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 80, 0);
        gd.addImage(icon);
        gd.addMessage("Channels selection", Font.getFont("Monospace"), Color.blue);
        gd.addStringField("Chanel name : ", "w2Laser 491 GFP", 20);
        String[] methods = AutoThresholder.getMethods();
        gd.addMessage("Dots detection parameters", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min dots size (µm3) : ", minDots, 3);
        gd.addNumericField("Max dots size (µm3) : ", maxDots, 3);
        gd.addChoice("Thresholding method :", methods, thMet);
        gd.addMessage("Difference of Gaussian (radius1 < radius2)", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("radius 1 (pixels) : ", sigma1, 2);
        gd.addNumericField("radius 2 (pixels) : ", sigma2, 2);
           gd.addNumericField("radius z (pixels) : ", sigmaz, 2);
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Calibration xy (µm)  :", cal.pixelWidth, 4);
        //if ( cal.pixelDepth == 1) cal.pixelDepth = 0.5;
        gd.addNumericField("Calibration z (µm)  :", cal.pixelDepth, 4);
        //gd.addMessage("Spatial distribution", Font.getFont("Monospace"), Color.blue);
        //gd.addNumericField("Radius for neighboring analysis : ", radiusNei, 2);
        //gd.addNumericField("Number of neighborgs : ", nbNei, 2);
        gd.showDialog();
        String chanel = gd.getNextString();
        minDots = gd.getNextNumber();
        maxDots = gd.getNextNumber();
        thMet = gd.getNextChoice();
        sigma1 = gd.getNextNumber();
        sigma2 = gd.getNextNumber();
        sigmaz = gd.getNextNumber();
        cal.pixelWidth = gd.getNextNumber();
        cal.pixelHeight = cal.pixelWidth;
        cal.pixelDepth = gd.getNextNumber();
        //radiusNei = gd.getNextNumber();
        //nbNei = (int)gd.getNextNumber();
        if (gd.wasCanceled())
                return null;
        return(chanel);
    }
    
    public double getZSphereRad(int z) {
        double dz = (z-zcent)*cal.pixelDepth;
        double r = rad*cal.pixelWidth;
        return (Math.sqrt(r*r-dz*dz))/cal.pixelWidth; // return in pixels
    }
    
    /** 
     * Find dots with DOG method
     * @param img channel
     * @return dots population
     */
    public Objects3DPopulation findDotsDoG(ImagePlus img) {
        IJ.run(img, "32-bit", "");
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLDOG = DOG(imgCL, sigma1, sigma2, sigmaz);
        clij2.release(imgCL);
        ImagePlus impDots = clij2.pull(imgCLDOG);
        clij2.release(imgCLDOG);
        Object3D_IJUtils obij = new Object3D_IJUtils();
        for (int z=1; z<=impDots.getNSlices(); z++){
            impDots.setSlice(z);
            double crad = getZSphereRad(z);
            OvalRoi roi = new OvalRoi(cent[0]-crad, cent[1]-crad, crad*2, crad*2);
            roi.setPosition(z);
            impDots.setRoi(roi);
            IJ.run(impDots, "Clear Outside", "slice");
        }
        IJ.run(impDots, "Select None", "");
        int mid = (int) zcent;
        impDots.setSlice(mid);
        double crad = getZSphereRad(mid);
        OvalRoi roi = new OvalRoi(cent[0]-crad, cent[1]-crad, crad*2, crad*2);
        roi.setPosition(mid);
        impDots.setRoi(roi);
        IJ.setAutoThreshold(impDots, thMet+" dark");
        Prefs.blackBackground = true;
        IJ.run(impDots, "Convert to Mask", "method="+thMet+" background=Dark");
        if (impDots.isInvertedLut()) IJ.run(impDots, "Invert LUT", "");
        Prefs.blackBackground = true;
        IJ.run(impDots, "Close-", "stack");
        impDots.setCalibration(cal);
        Objects3DPopulation dotPop = new Objects3DPopulation(getPopFromImage(impDots).getObjectsWithinVolume(minDots, maxDots, true));
        closeImages(impDots);
        IJ.run(img, "16-bit", "");
        return(dotPop);
    } 
    

    
    /**
     * Create cells population image
     * @param cellPop
     * @param img
     * @param pathName

     */
    public void saveDotsImage(Objects3DPopulation dotPop, ImagePlus img, String pathName) {
        IJ.run(img, "Select All", "");
        IJ.run(img, "Clear", "stack");
        IJ.run(img, "Select None", "");
         for (int z=1; z<=img.getNSlices(); z++){
            img.setSlice(z);
            double crad = getZSphereRad(z); // in pixels
            OvalRoi roi = new OvalRoi(cent[0]-crad, cent[1]-crad, crad*2, crad*2);
            ImageProcessor ip = img.getProcessor();
            ip.setRoi(roi);
            ip.setValue(150);
            roi.drawPixels(ip);
           img.setProcessor(ip);
        }
        ImageHandler imh = ImageHandler.wrap(img);
        dotPop.draw(imh, 255);
        // Save
        FileSaver imgDots = new FileSaver(imh.getImagePlus());
        imgDots.saveAsTiff(pathName);
        imh.closeImagePlus();
        closeImages(img);
    }
  
    public void getCenter(FloatPolygon fp) {
        cent[0] = 0;
        cent[1] = 0;
        for (int i=0; i < fp.xpoints.length; i++) {
            cent[0] += fp.xpoints[i];
            cent[1] += fp.ypoints[i];
        }
        cent[0] /= fp.xpoints.length;
        cent[1] /= fp.xpoints.length;
    }
    
    public void getSphereFromRoi(Roi roi) {
        zcent = roi.getZPosition();
        rad = roi.getLength()/(2*Math.PI);
        getCenter(roi.getInterpolatedPolygon());
        //System.out.println(roi.getZPosition()+" "+rad+" "+cent[0]+" "+cent[1]);
    }
    
    
   /** public void getDistNeighbors(Object3D obj, Objects3DPopulation pop, DescriptiveStatistics cellNbNeighborsDistMean, 
           DescriptiveStatistics cellNbNeighborsDistMax) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        double[] dist= pop.kClosestDistancesSquared(obj.getCenterX(), obj.getCenterY(), obj.getCenterZ(), nbNei);
        for (double d : dist) {
           stats.addValue(Math.sqrt(d)); 
        }
        cellNbNeighborsDistMax.addValue(stats.getMax());
        cellNbNeighborsDistMean.addValue(stats.getMean());
    }*/
     
     /**
     * Write headers for results file
     * 
     * @param outDirResults
    */
    public void writeHeaders(String outDirResults) throws IOException {
        // global results
        FileWriter fileResults = new FileWriter(outDirResults +"Results.xls", false);
        outPutResults = new BufferedWriter(fileResults);
        outPutResults.write("Image Name\tOocyte Volume(um3)\tNb spots\t" 
                             +"Mean spots volume(um3)\tSD spots volume\tMean distance to center(um)\tSD dist to center " 
                + "\tMean dist to closest dot(um)\tSD dist to closest dot \n");
        outPutResults.flush();
        
        // indivs results
        FileWriter fileDistances = new FileWriter(outDirResults +"ResultsDistances.xls", false);
        outPutDistances = new BufferedWriter(fileDistances);
        outPutDistances.write("Image name\tDistanceToCenter\tDotVolume\tDistanceToClosestNeighbor \n");
        outPutDistances.flush();
        
    }
    
    public void countSpots(Objects3DPopulation dotsPop, ImagePlus img, String imgName, String outDirResults) throws IOException 
    {
        double dotsVol = 0;
        double dist = 0;
        int ndots = dotsPop.getNbObjects();
        double[] vols = new double[ndots];
        double[] dists = new double[ndots];
        double[] dneis = new double[ndots];
        double dnei = 0;
        for (int i = 0; i < ndots; i++) {
            IJ.showProgress(i, ndots);
            Object3D dot = dotsPop.getObject(i);
            // object volume
            dotsVol += dot.getVolumeUnit();
            vols[i] = dot.getVolumeUnit();
            // distance to center
            dists[i] = dot.distPixelCenter(cent[0], cent[1], zcent);
            dist += dists[i];
            // distance to neighbor
            Object3D nei = dotsPop.closestCenter(dot, true);
            dneis[i] = nei.distCenterUnit(dot);
            dnei += dneis[i];
            // write individual stats
            outPutDistances.write(imgName+"\t"+dists[i]+"\t"+vols[i]+"\t"+dneis[i]+"\n");
         }
        outPutDistances.flush();
        outPutResults.write(imgName+"\t"+(rad*rad*rad*4/3*Math.PI*Math.pow(cal.pixelWidth,3))+"\t"+ndots
                + "\t"+(dotsVol/ndots)+"\t"+getSD(vols, dotsVol/ndots)+"\t"+(dist/ndots)+"\t"+getSD(dists, dist/ndots)
                + "\t"+(dnei/ndots)+"\t"+getSD(dneis, dnei/ndots)+"\n");      
        outPutResults.flush();
    }
    
    
    public void closeResults() throws IOException {
       outPutResults.close();
       outPutDistances.close();         
    }
}
