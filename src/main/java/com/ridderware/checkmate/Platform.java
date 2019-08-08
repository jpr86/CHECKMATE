/*
 * 
 * Coadaptive Heterogeneous simulation Engine for Combat Kill-webs and 
 * Multi-Agent Training Environment (CHECKMATE)
 *
 * Copyright 2006 Jeff Ridder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ridderware.checkmate;

import com.ridderware.checkmate.CMAgent.ColorzEnum;
import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import com.ridderware.fuse.Double2D;
import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.MutableDouble3D;
import com.ridderware.fuse.gui.Paintable;
import com.ridderware.fuse.gui.Painter;
import org.apache.logging.log4j.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Abstract base class for all platform agents.  Platforms are physical objects
 * that have geographical presence in the simulation.  As of 09/07/2006, all
 * platforms can also have a superior and multiple subordinates.  When creating
 * subclasses, this hierarchical structure can be used for simple aggregation,
 * command and control behaviors, etc.
 *
 *  @author Jeff Ridder
 *
 */
public class Platform extends CMAgent implements Paintable, ISIMDIS
{
    public enum PlatformType
    {
        GROUND,
        AIR,
        SEA,
        UNDERSEA,
        SPACE
    }
    
    private static final Logger logger = LogManager.getLogger(Platform.class);

    private HashSet<String> paintable_views = new HashSet<>();

    private Platform superior;

    private PlatformType platformType;

    //  This is to support fromXML serialization and later association with
    //  a superior Platform object.
    private int superior_id;

    private HashSet<Platform> subordinates = new HashSet<>();

    /**
     * Enum of possible status values.
     */
    public enum Status
    {
        /**
         *  Active status.
         */
        ACTIVE,
        /**
         *  Inactive status.
         */
        INACTIVE,
        /**
         *  Dead status.
         */
        DEAD
    }

    /**
     *  Location of the object.
     */
    private MutableDouble3D location = new MutableDouble3D(0., 0., 0.);

    /**
     *  Point value of the object.
     */
    private int points;

    /**
     *  Status of the object.
     */
    private Status status;

    private Font ttf;

    private String fontSymbol;

    private Color color;

    private String simdis_icon;

    /**
     * Creates a new instance of Platform.
     * @param name name of the platform.
     * @param world CMWorld in which the platform plays.
     * @param points point value of the platform.
     */
    public Platform(String name, CMWorld world, int points)
    {
        super(name, world);
        this.points = points;
        this.status = Status.ACTIVE;
        this.superior = null;
        this.superior_id = -1;
        this.simdis_icon = null;
        this.platformType = PlatformType.GROUND;
        this.ttf = null;
    }

    /**
     * Creates a new instance of Platfrom with attributes for graphical display.
     * @param name name of the platform.
     * @param world CMWorld in which the platform plays.
     * @param points point value of the platform.
     * @param ttf font containing the symbol to display.
     * @param fontSymbol the font symbol to display for this object.
     * @param color color to use for this symbol.
     */
    public Platform(String name, CMWorld world, int points, Font ttf,
        String fontSymbol, Color color)
    {
        this(name, world, points);
        this.ttf = ttf;
        this.fontSymbol = fontSymbol;
        this.color = color;
        this.simdis_icon = null;
    }

    /**
     * @return the platformType
     */
    public PlatformType getPlatformType()
    {
        return platformType;
    }

    /**
     * @param platformType the platformType to set
     */
    public void setPlatformType(PlatformType platformType)
    {
        this.platformType = platformType;
    }
    
    /**
     * Adds a paintable view to the platform.
     *
     * @param paintable_view name of the paintable view.
     */
    public void addPaintableView(String paintable_view)
    {
        this.paintable_views.add(paintable_view);
    }

    /**
     * Returns the set of paintable views.
     *
     * @return Set of paintable views.
     */
    public HashSet<String> getPaintableViews()
    {
        return this.paintable_views;
    }

    /**
     * See the base class in the FUSE framework.
     * @return ditto.
     */
    @Override
    public int getMaxBufferSize()
    {
        return 35;
    }

    /**
     * Returns the symbol color.
     * @return color.
     */
    public Color getColor()
    {
        return this.color;
    }

    /**
     * Returns the font.
     * @return font.
     */
    public Font getFont()
    {
        return this.ttf;
    }

    /**
     * Returns the font symbol.
     * @return font symbol.
     */
    public String getFontSymbol()
    {
        return this.fontSymbol;
    }

    /**
     * Sets the SIMDIS icon.
     * @param simdis_icon string name of the SIMDIS icon.
     */
    public void setSIMDISIcon(String simdis_icon)
    {
        this.simdis_icon = simdis_icon;
    }

    /**
     * Returns the SIMDIS icon.
     * @return string name of the SIMDIS icon.
     */
    public String getSIMDISIcon()
    {
        return this.simdis_icon;
    }

    /**
     * See the base class.
     * @return ditto.
     */
    @Override
    public Paintable.PaintType getPaintType()
    {
        return Paintable.PaintType.Simple;
    }

    /**
     * Called by the FUSE framework to paint the agent.
     * @param args see base class documentation.
     * @return a collection of shapes.
     */
    @Override
    public Collection<Shape> paintAgent(Object... args)
    {
        HashSet<Shape> boundingShapes = new HashSet<Shape>(1);
        if (this.getStatus() == Status.ACTIVE)
        {
            Double3D loc = null;
            if (CMWorld.getEarthModel().getCoordinateSystem().
                equalsIgnoreCase("ENU"))
            {
                loc = this.getLocation();
            }
            else if (CMWorld.getEarthModel().getCoordinateSystem().
                equalsIgnoreCase("LLA"))
            {
                loc = new Double3D(getLocation().getY(), getLocation().getX(), getLocation().
                    getZ());
            }
            if (loc != null)
            {
                boundingShapes.add(Painter.getPainter().paintText(fontSymbol,
                    loc, color, ttf, 0.));
            }
        }
        return boundingShapes;
    }

    /**
     * Writes the initialization strings to the SIMDIS .asi file.
     * @param asi_file the SIMDIS .asi file.
     */
    @Override
    public void simdisInitialize(File asi_file)
    {
        if (this.getSIMDISIcon() != null && asi_file != null)
        {
            try
            {
                FileWriter fw = new FileWriter(asi_file, true);
                PrintWriter pw = new PrintWriter(fw);

                pw.println("PlatformID\t" + this.getId());
                pw.println("PlatformName\t" + this.getId() + "\t" +
                    this.getName());
                pw.println("PlatformIcon\t" + this.getId() + "\t" +
                    this.getSIMDISIcon());
                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }

    /**
     * Writes the time data to the SIMDIS .asi file.
     * @param time time at which to write the data.
     * @param asi_file SIMDIS .asi file.
     */
    @Override
    public void simdisUpdate(double time, File asi_file)
    {
        if (this.getSIMDISIcon() != null && asi_file != null &&
            this.getStatus() == Platform.Status.ACTIVE)
        {
            try
            {
                String simdis_output = "";

                if (CMWorld.getEarthModel().getCoordinateSystem().
                    equalsIgnoreCase("ENU"))
                {
                    simdis_output += LengthUnit.NAUTICAL_MILES.convert(getLocation().
                        getX(), LengthUnit.METERS) +
                        "\t" + LengthUnit.NAUTICAL_MILES.convert(getLocation().
                        getY(), LengthUnit.METERS) + "\t" + LengthUnit.NAUTICAL_MILES.convert(getLocation().
                        getZ(), LengthUnit.METERS);
                }
                else if (CMWorld.getEarthModel().getCoordinateSystem().
                    equalsIgnoreCase("LLA"))
                {
                    //  round earth
                    simdis_output += getLocation().getX() +
                        "\t" + getLocation().getY() +
                        "\t" + LengthUnit.NAUTICAL_MILES.convert(getLocation().
                        getZ(), LengthUnit.METERS);
                }


                FileWriter fw = new FileWriter(asi_file, true);
                PrintWriter pw = new PrintWriter(fw);

                pw.println("PlatformData\t" + String.valueOf(this.getId()) +
                    "\t" + String.valueOf(time) + "\t" +
                    simdis_output);
                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }

    /**
     * Returns the set of subordinate platforms.
     * @return HashSet containing subordinates.
     */
    public HashSet<Platform> getSubordinates()
    {
        return subordinates;
    }

    /**
     * Adds a subordinate platform.
     * @param platform subordinate to be added.
     */
    public void addSubordinate(Platform platform)
    {
        this.subordinates.add(platform);
    }

    /**
     * Removes a subordinate platform.
     * @param platform subordinate to be removed.
     */
    public void removeSubordinate(Platform platform)
    {
        this.subordinates.remove(platform);
    }

    /**
     * Returns the superior platform.
     * @return the superior platform.
     */
    public Platform getSuperior()
    {
        return superior;
    }

    /**
     * Sets the superior platform.  Each platform can have only one superior.
     * @param superior the superior platform.
     */
    public void setSuperior(Platform superior)
    {
        this.superior = superior;
    }

    /**
     * Returns the unique ID of the superior platform.  This is used primarily to
     * support contstruction of superior/subordinate hierarchies following reading
     * of scenarios.
     * @return the ID of the superior.
     */
    public int getSuperiorId()
    {
        return this.superior_id;
    }

    /**
     * Sets the initial attributes of the platform before each simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();
        this.status = Status.ACTIVE;
    }

    /**
     * Sets the location of the platform on the ground.
     * @param location a Double2D object containing the location.
     */
    public void setLocation(Double2D location)
    {
        this.setLocation(new Double3D(location));
    }

    /**
     * Sets the location of the platform.  All units are in nautical miles.
     * @param location a Double3D object containing the location.
     */
    public void setLocation(Double3D location)
    {
        this.location.setXYZ(location);

        //  Sets the elevation of ground platforms to the elevation of the terrain.
        if (this.getPlatformType() == PlatformType.GROUND)
        {
            this.location.setZ(LengthUnit.METERS.convert(CMWorld.getTerrainModel().elevation(location.getX(), location.getY()), LengthUnit.NAUTICAL_MILES));
        }

        if (this.status == Status.ACTIVE && getUniverse() != null &&
            this.getSIMDISIcon() != null)
        {
            simdisUpdate(getUniverse().getCurrentTime(), getWorld().getASIFile());
        }
    }

    /**
     * Sets the color that this platform will be painted
     * @param color the color to paint this platform
     */
    public void setColor(Color color)
    {
        this.color = color;
    }

    /**
     * Sets the font symbol that dictates the appearance of this platform
     * @param fontSymbol the font symbol corresponding to the
     * {@link #getFont()} font this platform uses.
     */
    public void setFontSymbol(String fontSymbol)
    {
        logger.debug("Setting font symbol to [" + fontSymbol + "] " +
            "[" + this.getClass().getSimpleName() + "]");
        this.fontSymbol = fontSymbol;
    }

    /**
     * Sets the font that this platform uses to draw itself with.
     * @param font the Font this platform will use to draw itself with, the
     * symbol/character within the font that will be used is dictated by the
     * font symbol used {@link #getFontSymbol()}
     */
    public void setFont(Font font)
    {
        this.ttf = font;
    }

    /**
     * Returns the location of the platform.
     * @return a MutableDouble3D object.
     */
    public MutableDouble3D getLocation()
    {
        return this.location;
    }

    /**
     * Sets the point value of the platform.
     * @param points points.
     */
    public void setPoints(int points)
    {
        this.points = points;
    }

    /**
     * Returns the point value of the platform.
     * @return points.
     */
    public int getPoints()
    {
        return this.points;
    }

    /**
     * Sets the platform status to ACTIVE, INACTIVE, or DEAD.
     * @param status platfrom status.
     */
    public void setStatus(Status status)
    {
        this.status = status;
    }

    /**
     * Returns the status of the platform.
     * @return status.
     */
    public Status getStatus()
    {
        return this.status;
    }

    /**
     * Sets the platform's attributes by parsing the XML.
     * @param node root node for the platform.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();

        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);

            if (child.getNodeName().equalsIgnoreCase("points"))
            {
                this.setPoints(Integer.parseInt(child.getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("font-symbol"))
            {
                char[] sym = {(char) Integer.parseInt(child.getTextContent())};

                String fs = new String(sym);

                this.setFontSymbol(fs);
            }
            else if (child.getNodeName().equalsIgnoreCase("font-size"))
            {
                float size = Float.parseFloat(child.getTextContent());

                ttf = getWorld().getFont(size);
            }
            else if (child.getNodeName().equalsIgnoreCase("color"))
            {
                this.setColor(ColorzEnum.getColor(child.getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("superior-id"))
            {
                this.superior_id = Integer.parseInt(child.getTextContent());
            }
            else if (child.getNodeName().equalsIgnoreCase("simdis-icon"))
            {
                this.setSIMDISIcon(child.getTextContent());
            }
        }
    }

    /**
     * Creates XML nodes containing the platform's attributes.
     *
     * @param node root node for the platform.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e = document.createElement("points");
        e.setTextContent(String.valueOf(this.points));
        node.appendChild(e);

        e = document.createElement("font-symbol");
        e.setTextContent(String.valueOf(this.fontSymbol.codePointAt(0)));
        node.appendChild(e);

        e = document.createElement("font-size");
        e.setTextContent(String.valueOf(ttf.getSize()));
        node.appendChild(e);

        e = document.createElement("color");
        e.setTextContent(String.valueOf(this.color.getRed()) + "," +
            String.valueOf(this.color.getGreen()) + "," +
            String.valueOf(this.color.getBlue()));
        node.appendChild(e);

        if (this.getSIMDISIcon() != null)
        {
            e = document.createElement("simdis-icon");
            e.setTextContent(this.getSIMDISIcon());
            node.appendChild(e);
        }

        if (this.getSuperior() != null)
        {
            e = document.createElement("superior-id");
            e.setTextContent(String.valueOf(this.superior.getId()));
            node.appendChild(e);
        }
    }
}
