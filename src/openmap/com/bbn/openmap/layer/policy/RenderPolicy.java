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
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/layer/policy/RenderPolicy.java,v $
// $RCSfile: RenderPolicy.java,v $
// $Revision: 1.1 $
// $Date: 2003/03/10 22:03:57 $
// $Author: dietrick $
// 
// **********************************************************************


package com.bbn.openmap.layer.policy;

import com.bbn.openmap.layer.OMGraphicHandlerLayer;
import com.bbn.openmap.omGraphics.OMGraphicList;
import java.awt.Graphics;

/**
 * A policy object that can be used by a layer to figure out the best
 * way to paint on the map.
 */
public interface RenderPolicy {

    public OMGraphicHandlerLayer getLayer();

    /**
     * Called when an OMGraphicHandlerLayer should begin preparing
     * OMGraphics for the map.  This is a hook into the list to help
     * RenderPolicy make decisions or set up the list for faster
     * rendering.
     */
    public OMGraphicList prepare();

    /**
     * Called from OMGraphicHandlerLayer.paint(Graphics), so the
     * policy can handle the painting for the layer.
     */
    public void paint(Graphics g);

}