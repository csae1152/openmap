// **********************************************************************
// 
// <copyright>
// 
//  BBN Technologies, a Verizon Company
//  10 Moulton Street
//  Cambridge, MA 02138
//  (617) 873-8000
// 
//  Copyright (C) BBNT Solutions LLC. All rights reserved.
// 
// </copyright>
// **********************************************************************
// 
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/layer/vpf/VPFLayer.java,v $
// $RCSfile: VPFLayer.java,v $
// $Revision: 1.2 $
// $Date: 2003/02/20 02:43:50 $
// $Author: dietrick $
// 
// **********************************************************************


package com.bbn.openmap.layer.vpf;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import javax.swing.*;
import com.bbn.openmap.LatLonPoint;
import com.bbn.openmap.Layer;
import com.bbn.openmap.event.*;
import com.bbn.openmap.layer.OMGraphicHandlerLayer;
import com.bbn.openmap.layer.util.LayerUtils;
import com.bbn.openmap.omGraphics.*;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.util.Debug;
import com.bbn.openmap.util.PaletteHelper;
import com.bbn.openmap.util.PropUtils;
import com.bbn.openmap.util.SwingWorker;

/**
 * Implement an OpenMap Layer for display of NIMA data sources in the
 * VPF (Mil-Std 2407) format. <p>
 * The properties needed to configure this layer to display VPF data include
 * some "magic" strings specific to the VPF database you are trying to 
 * display.  {@link com.bbn.openmap.layer.vpf.DescribeDB DescribeDB} is 
 * a utility to help you figure out what those strings are.
 * <pre>
 *#-----------------------------
 *# Properties for a VMAP political layer
 *#-----------------------------
 *# Mandatory properties
 *# Mandatory for all layers
 *vmapPol.class= com.bbn.openmap.layer.vpf.VPFLayer
 *vmapPol.prettyName= Political Boundaries from VMAP
 *# Mandatory - choose .vpfPath or .libraryBean
 *# .vpfPath specifies a ';' separated list of paths to data, each of these
 *#directories should have a "lat" or "lat." file in them.
 *vmapPol.vpfPath= e:/VMAPLV0
 *# .libraryBean specifies a separate object in the properties file that
 *# locates vpf data.  You should use this option if you have multiple VPF
 *# layers displaying the same VPF database.  (For example, you have 3 VMAP
 *# layers, displaying coastlines, railroads and rivers.  Each layer would
 *# then specify the same libraryBean name.  This reduces the memory 
 *# consumption of the VPF layers.)
 *# See {@link com.bbn.openmap.layer.vpf.LibraryBean LibraryBean javadoc} for properties info. (Example below)
 *vmapPol.libraryBean= VMAPdata
 *VMAPData.class=com.bbn.openmap.layer.vpf.LibraryBean
 *VMAPData.vpfPath=e:/VMAPLV0
 *# Don't forget to add VMAPData to the openmap.components property list, too.
 *#
 *#Optional (default is false) changes how features are located.  Should
 *#use this option for multiple coverage types, or when null pointer errors
 *#are encountered.
 *vmapPol.searchByFeature=true
 *#
 *# Choose either .defaultLayer or .coverageType
 *#
 *# .defaultLayer results in the layer looking up the remainder of the
 *# properties in the defaultVPFLayers.properties files.
 *vmapPol.defaultLayer= vmapPolitical
 *#
 *# .coverageType continues in this property file - chose the VMAP bnd
 *# (Boundary) coverage
 *vmapPol.coverageType= bnd
 *# Select if we want edges (polylines), areas (filled polygons) or text
 *# This is a space-separated list of "edge" "area" "text" "epoint" and "cpoint"
 *vmapPol.featureTypes= edge area
 *#For DCW, the remaining 3 properties are ignored
 *#Select the text featureclasses we'd like to display.  Since we didn't
 *#select text above, this is ignored
 *vmapPol.text= 
 *#Select the edge featureclasses we'd like to display. In this case,
 *#draw political boundaries and coastline, but skip anything else.
 *vmapPol.edge=polbndl coastl
 *#Select the area featureclasses we'd like to display. In this case,
 *#draw politcal areas, but skip anything else.
 *vmapPol.area=polbnda
 *#Selectable drawing attributes - for default values, they don't need to
 *#be included.  A hex ARGB color looks like FF000000 for black.
 *vmapPol.lineColor=hex ARGB color value - Black is default.
 *vmapPol.fillColor=hex ARGB color value - Clear is default.
 *vmapPol.lineWidth=float value, 1f is the default, 10f is the max.
 *#------------------------------------
 *# End of properties for a VMAP political layer
 *#------------------------------------
 *
 *### Now a VMAP Coastline layer
 *vmapCoast.class=com.bbn.openmap.layer.vpf.VPFLayer
 *vmapCoast.prettyName=VMAP Coastline Layer
 *vmapCoast.vpfPath=/u5/vmap/vmaplv0
 *## a predefined layer from the VPF predefined layer set found in
 *## com/bbn/openmap/layer/vpf/defaultVPFLayers.properties
 *vmapCoast.defaultLayer=vmapCoastline
 *
 *### Now a DCW Political layer
 *# Basic political boundaries with DCW
 *dcwPolitical.class=com.bbn.openmap.layer.vpf.VPFLayer
 *dcwPolitical.prettyName=DCW Political Boundaries
 *dcwPolitical.vpfPath=path to data
 *dcwPolitical.coverageType=po
 *dcwPolitical.featureTypes=edge area
 * </pre>
 */

public class VPFLayer extends OMGraphicHandlerLayer
    implements ProjectionListener, ActionListener, Serializable
{
    /** property extension used to set the VPF root directory */
    public static final String pathProperty = "vpfPath";
    /** property extension used to set the desired coverage type. e.g. po, hyd */
    public static final String coverageTypeProperty = "coverageType";
    /** 
     * Property extension used to set the desired feature types.
     * e.g. line area text 
     */
    public static final String featureTypesProperty = "featureTypes";
    /** property extension used to specify a default property set */
    public static final String defaultLayerProperty = "defaultLayer";
    /** 
     * Property that lets you search for graphics via feature type.
     * Dangerously slow for features that have many graphics spread
     * out over several tiles. Set to true to search by feature,
     * false (default) to get the tiles first, and then look for
     * graphics. 
     */
    public static final String searchByFeatureProperty = "searchByFeature";
    /** alternate method for setting VPF data path */
    public static final String libraryProperty = "libraryBean";
    /** Method for setting VPF cutoff scale */
    public static final String cutoffScaleProperty = "cutoffScale";
    /** the object that knows all the nitty-gritty vpf stuff */
    transient LibrarySelectionTable lst;
    /** our own little graphics factory */
    private transient LayerGraphicWarehouseSupport warehouse;
    /** are we searching by feature table (true) or tile (false) */
    protected boolean searchByFeatures = false;
    /** the name of the data bean to look for in beancontext */
    private String libraryBeanName = null;

    /** hang onto prefix used to initialize warehouse in setProperties() */
    private String prefix;
    /** hang onto properties file used to initialize warehouse */
    private Properties props;

    /** the path to the root VPF directory */
    protected String[] dataPaths = null;
    /** the coverage type that we display */
    protected String coverageType = "po";

    protected int cutoffScale = LibrarySelectionTable.DEFAULT_BROWSE_CUTOFF;

    /**
     *  Construct a VPF layer.
     */
    public VPFLayer() {}

    /**
     * Construct a VPFLayer, and sets its name.
     * @param name the name of the layer.
     */
    public VPFLayer(String name) {
	this();
	setName(name);
    }

    /**
     * Overriding what happens to the internal OMGraphicList when the
     * projection changes.  For this layer, we want to reset the
     * internal OMGraphicList when the projection changes.
     */
    protected void resetListForProjectionChange() {
	setList(null);
    }

    /**
     * Sets the features (lines, areas, text, points) that get displayed.
     * @param features a whitespace-separated list of features to display.
     */
    public void setFeatures(String features) {
        warehouse.setFeatures(features);
    }

    /**
     * Another way to set the parameters of the DcwLayer.
     * @see #pathProperty
     * @see #coverageTypeProperty
     * @see #featureTypesProperty
     */
    public void setProperties(String prefix, java.util.Properties props) {
	super.setProperties(prefix, props);
	setAddToBeanContext(true);

	String realPrefix = PropUtils.getScopedPropertyPrefix(prefix);

	String path[] = 
	    LayerUtils.initPathsFromProperties(props, realPrefix + pathProperty);

	if (path != null) {
	    setPath(path);
	}
        String defaultProperty = 
	    props.getProperty(realPrefix + defaultLayerProperty);

	if (defaultProperty != null) {
	    prefix = defaultProperty;
	    props = getDefaultProperties();
	}

	//need to save these so we can call setProperties on the warehouse,
	//which we probably can't construct yet
	this.prefix = prefix;
	this.props = props;

	String coverage = props.getProperty(realPrefix + coverageTypeProperty);
	if (coverage != null) {
	  setDataTypes(coverage);
	}

	cutoffScale = LayerUtils.intFromProperties(props, realPrefix + cutoffScaleProperty, cutoffScale);

	//	searchByFeatureProperty
	searchByFeatures = LayerUtils.booleanFromProperties(props, realPrefix + searchByFeatureProperty, searchByFeatures);

	libraryBeanName = props.getProperty(realPrefix + libraryProperty);

	try {
	    //force lst and warehosue to re-init with current properties
	    lst = null;
	    warehouse = null;
	    initLST();
	} catch (IllegalArgumentException iae){
	    Debug.error("VPFLayer.setProperties: Illegal Argument Exception.\n\nPerhaps a file not found.  Check to make sure that the paths to the VPF data directories are the parents of \"lat\" or \"lat.\" files. \n\n" + iae);
	}
    }

    /** Where we store our default properties once we've loaded them. */
    private static java.util.Properties defaultProps;

    /**
     * Return our default properties for vpf land.
     */
    public static java.util.Properties getDefaultProperties() {
        if (defaultProps == null) {
  	    try {
	        InputStream in = VPFLayer.class.getResourceAsStream("defaultVPFlayers.properties");
		//use a temporary so other threads won't see an
		//empty properties file
		java.util.Properties tmp = new java.util.Properties();
		if (in != null) {
		    tmp.load(in);
		    in.close();
		} else {
		    Debug.error("VPFLayer: can't load default properties file");
		    //just use an empty properties file
		}
		defaultProps = tmp;
	    } catch (IOException io) {
	        Debug.error("VPFLayer: can't load default properties: "
			     + io);
		defaultProps = new java.util.Properties();
	    }
	}
	return defaultProps;
    }

    /**
     * Set the data path to a single place.
     */
    public void setPath(String newPath) {
	dataPaths = new String[]{newPath};
    }

    /**
     * Set the data path to multiple places.
     */
    public void setPath(String[] newPaths) {
	dataPaths = newPaths;
    }

    /**
     * Returns the list of paths we use to look for data.
     * @return the list of paths.  Don't modify the array!
     */
    public String[] getPath() {
	return dataPaths;
    }

    /**
     * Set the coveragetype of the layer.
     * @param dataTypes the coveragetype to display.
     */
    public void setDataTypes(String dataTypes) {
	coverageType = dataTypes;
    }

    /**
     * Get the current coverage type.
     * @return the current coverage type.
     */
    public String getDataTypes() {
	return coverageType;
    }

    /**
     * Enable/Disable the display of areas.
     */
    public void setAreasEnabled(boolean value) {
	warehouse.setAreaFeatures(value);
    }

    /**
     * Find out if areas are enabled.
     */
    public boolean getAreasEnabled() {
	return warehouse.drawAreaFeatures();
    }

    /**
     * Enable/Disable the display of edges.
     */
    public void setEdgesEnabled(boolean value) {
	warehouse.setEdgeFeatures(value);
    }

    /**
     * Find out if edges are enabled.
     */
    public boolean getEdgesEnabled() {
	return warehouse.drawEdgeFeatures();
    }

    /**
     * Enable/Disable the display of entity points.
     */
    public void setEPointsEnabled(boolean value) {
	warehouse.setEPointFeatures(value);
    }

    /**
     * Find out if entity points are enabled.
     */
    public boolean getEPointsEnabled() {
	return warehouse.drawEPointFeatures();
    }

    /**
     * Enable/Disable the display of connected points.
     */
    public void setCPointsEnabled(boolean value) {
	warehouse.setCPointFeatures(value);
    }

    /**
     * Find out if connected points are enabled.
     */
    public boolean getCPointsEnabled() {
	return warehouse.drawCPointFeatures();
    }

    /**
     * Enable/Disable the display of text.
     */
    public void setTextEnabled(boolean value) {
	warehouse.setTextFeatures(value);
    }

    /**
     * Find out if text is enabled.
     */
    public boolean getTextEnabled() {
	return warehouse.drawTextFeatures();
    }

    /**
     * Get the DrawingAttributes used for the coverage type.
     */
    public DrawingAttributes getDrawingAttributes(){
	return warehouse.getDrawingAttributes();
    }

    /**
     * Set the drawing attributes for the coverage type.
     */
    public void setDrawingAttributes(DrawingAttributes da){
	warehouse.setDrawingAttributes(da);
    }

    /**
     * initialize the library selection table.
     */
    private void initLST() {
	try {
 	    if (lst == null) {
		if (libraryBeanName != null) {
		    LibraryBean libraryBean = null;
		    Collection beanContext = getBeanContext();
		    if (beanContext == null) {
			//no bean context yet
			return;
		    }
		    for (Iterator i = beanContext.iterator(); i.hasNext(); ) {
			Object obj = i.next();
			if (obj instanceof LibraryBean) {
			    LibraryBean lb = (LibraryBean)obj;
			    if (libraryBeanName.equals(lb.getName())) {
				libraryBean = lb;
				break;
			    }
			}
		    }
		    if (libraryBean != null) {
			lst = libraryBean.getLibrarySelectionTable();
		    } else {
			Debug.output("VPFLayer.init: Couldn't find libraryBean " + libraryBeanName + " to read VPF data");
		    }
		} else {
		    if (dataPaths == null) {
			Debug.output("VPFLayer|"+getName()+ ": path not set");
		    } else {
			lst = new LibrarySelectionTable(dataPaths);
			lst.setCutoffScale(cutoffScale);
		    }
		}

		if (lst != null) {
		    if (lst.getDatabaseName().equals("DCW")) {
			warehouse = new VPFLayerDCWWarehouse();
		    } else if (searchByFeatures) {
			warehouse = new VPFFeatureGraphicWarehouse();
		    } else {
			warehouse = new VPFLayerGraphicWarehouse();
		    }
		    warehouse.setProperties(prefix, props);
		}
	    }
	} catch (com.bbn.openmap.io.FormatException f) {
 	    throw new java.lang.IllegalArgumentException(f.getMessage());
//  	} catch (NullPointerException npe) {
//  	    throw new java.lang.IllegalArgumentException("VPFLayer|" + getName() +
//  							 ": path name not valid");
	}
    }      

    /**
     * Use doPrepare() method instead.  This was the old method call
     * to do the same thing doPrepare is now doing, from the
     * OMGraphicHandler superclass.  doPrepare() launches a thread to
     * do the work.
     * @deprecated use doPrepare() instead of computeLayer();
     */
    public void computeLayer() {
	doPrepare();
    }

    /**
     * Use prepare instead.  This was the old method call to do the
     * same thing prepare() is now doing.
     * @deprecated use prepare() instead of getRectangle();
     */
    public OMGraphicList getRectangle() {
	return prepare();
    }

    /**
     * Create the OMGraphicList to use on the map.  OMGraphicHandler
     * methods call this.
     */
    public OMGraphicList prepare() {
        if (lst == null) {
	    try {
		initLST();
	    } catch (IllegalArgumentException iae){
		Debug.error("VPFLayer.getRectangle: Illegal Argument Exception.\n\nPerhaps a file not found.  Check to make sure that the paths to the VPF data directories are the parents of \"lat\" or \"lat.\" files. \n\n" + iae);
		return null;
	    }
	}
	if (warehouse == null){
	    StringBuffer dpb = new StringBuffer();
	    if (dataPaths != null) {
		for (int num = 0; num < dataPaths.length; num++) {
		    if (num > 0) {
			dpb.append(":");
		    }
		    dpb.append(dataPaths[num]);
		}
	    }

	    Debug.error("VPFLayer.getRectangle:  Data path probably wasn't set correctly (" + 
			dpb.toString() + ").  The warehouse not initialized.");
	    return null;
	}

	Projection p = getProjection();

	LatLonPoint upperleft = p.getUpperLeft();
	LatLonPoint lowerright = p.getLowerRight();
	if (Debug.debugging("vpfdetail")){
	    Debug.output("VPFLayer.getRectangle: " + 
			 coverageType /* + " " + dynamicArgs*/);
	}

	warehouse.clear();

// 	int edgecount[] = new int[] { 0 , 0 };
// 	int textcount[] = new int[] { 0 , 0 };
// 	int areacount[] = new int[] { 0 , 0 };

	// Check both dynamic args and palette values when
	// deciding what to draw.
 	if (Debug.debugging("vpf")) {
	    Debug.output("VPFLayer.getRectangle(): "
			 + "calling draw with boundaries: "
			 + upperleft + " " + lowerright);
	}
	long start = System.currentTimeMillis();

	StringTokenizer t = new StringTokenizer(coverageType);
	while (t.hasMoreTokens()) {
	    String currentCoverage = t.nextToken();
	    if (searchByFeatures) {
		lst.drawFeatures((int)p.getScale(),
				 p.getWidth(),
				 p.getHeight(),
				 currentCoverage, 
				 (VPFFeatureWarehouse) warehouse, 
				 upperleft, lowerright);
	    } else {
		lst.drawTile((int)p.getScale(),
			     p.getWidth(),
			     p.getHeight(),
 			     currentCoverage, 
			     warehouse, upperleft, lowerright);
	    }
	}
	long stop = System.currentTimeMillis();

//   	if (Debug.debugging("vpfdetail")) {
//  	    Debug.output("Returned " + edgecount[0] +
//  			 " polys with " + edgecount[1] + " points\n" +
//  			 "Returned " + textcount[0] +
//  			 " texts with " + textcount[1] + " points\n" +
//  			 "Returned " + areacount[0] +
//  			 " areas with " + areacount[1] + " points");
//  	}

 	if (Debug.debugging("vpf")) {
	    Debug.output("VPFLayer.getRectangle(): read time: " +
			 ((stop-start)/1000d) + " seconds");
	}

	OMGraphicList omglist = warehouse.getGraphics();

	// Don't forget to project.
	start = System.currentTimeMillis();
	omglist.project(p);
	stop = System.currentTimeMillis();

 	if (Debug.debugging("vpf")) {
	    Debug.output("VPFLayer.getRectangle(): proj time: "
			 + ((stop-start)/1000d) + " seconds");
	}
	return omglist;
    }

    private transient JPanel box;

    /**
     * Gets the palette associated with the layer.
     * @return Component or null
     */
    public Component getGUI() {
	initLST();
	if (warehouse == null) {
	    return (new javax.swing.JLabel("VPF Layer data not loaded properly."));
	}

	if (box == null) {

	    box = new JPanel();
	    box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
  	    box.setAlignmentX(Component.LEFT_ALIGNMENT);

	    JPanel titlePanel = new JPanel();
	    titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.X_AXIS));
	    JLabel title = new JLabel(getName());
	    title.setBorder(new javax.swing.border.EmptyBorder(2, 10, 2, 2));
	    titlePanel.add(title);
	    titlePanel.add(Box.createHorizontalGlue());
	    box.add(titlePanel);

	    JPanel stuff = new JPanel();
//  	    stuff.setLayout(new BoxLayout(stuff, BoxLayout.X_AXIS));
//    	    stuff.setAlignmentX(Component.LEFT_ALIGNMENT);

	    JPanel pal = null;
	    ActionListener al = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		    int index = Integer.parseInt(
			e.getActionCommand(), 10);
		    switch (index) {
		    case 0:
			warehouse.setEdgeFeatures(!warehouse.drawEdgeFeatures());
			break;
		    case 1:
			warehouse.setTextFeatures(!warehouse.drawTextFeatures());
			break;
		    case 2:
			warehouse.setAreaFeatures(!warehouse.drawAreaFeatures());
			break;
		    case 3:
			warehouse.setEPointFeatures(!warehouse.drawEPointFeatures());
			break;
		    case 4:
			warehouse.setCPointFeatures(!warehouse.drawCPointFeatures());
			break;
		    default:
			throw new RuntimeException("argh!");
		    }
		}
	    };

	    pal = PaletteHelper.createCheckbox(
// 		getName(),
		"Show:",
		new String[] {
		    VPFUtil.Edges,
		    VPFUtil.Text,
		    VPFUtil.Area,
		    VPFUtil.EPoint,
		    VPFUtil.CPoint
		},
		new boolean[] {
		    warehouse.drawEdgeFeatures(),
		    warehouse.drawTextFeatures(),
		    warehouse.drawAreaFeatures(),
		    warehouse.drawEPointFeatures(),
		    warehouse.drawCPointFeatures()
		},
		al
	    );

	    stuff.add(pal);
	    
	    if (lst != null) {
		Component warehouseGUI = warehouse.getGUI(lst);
		if (warehouseGUI != null) {
		    stuff.add(warehouseGUI);
		}
	    }
	    box.add(stuff);

	    JPanel pal2 = new JPanel();
	    JButton redraw = new JButton("Redraw Layer");
	    redraw.setActionCommand(RedrawCmd);
	    redraw.addActionListener(this);
	    pal2.add(redraw);
	    box.add(pal2);
	}
	return box;
    }
    
    public void actionPerformed(ActionEvent e) {
	String cmd = e.getActionCommand();
	if (cmd == RedrawCmd) {
	    setList(null);
	    doPrepare();
	} else {
	    super.actionPerformed(e);
	}
    }
}
