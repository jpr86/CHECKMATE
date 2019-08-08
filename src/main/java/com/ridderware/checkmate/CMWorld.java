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

import com.ridderware.jrandom.MersenneTwister;
import com.ridderware.jrandom.MersenneTwisterFast;
import com.ridderware.jrandom.RandomNumberGenerator;
import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import javax.xml.parsers.ParserConfigurationException;
import com.ridderware.fuse.IAgentFactory;
import com.ridderware.fuse.Cartesian2DSpace;
import com.ridderware.fuse.SimpleUniverse;
import com.ridderware.fuse.Universe;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import com.ridderware.fuse.Double2D;
import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.Scenario;
import com.ridderware.fuse.Space;
import com.ridderware.fuse.gui.FontManager;
import com.ridderware.fuse.gui.GUIUniverse;
import com.ridderware.fuse.gui.Paintable;
import com.ridderware.fuse.gui.SimpleView;
import com.ridderware.fuse.gui.ViewFrame;
import org.apache.logging.log4j.*;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/**
 * An AgentFactory for the CHECKMATE simulation.
 * @author Jeff Ridder
 */
public class CMWorld implements IAgentFactory
{
    //  Active Agents in the sim
    private final HashSet<Aircraft> aircraft = new HashSet<>();

    private final HashSet<Platform> targets = new HashSet<>();

    private final HashSet<Jammer> jammers = new HashSet<>();

    private final HashSet<Receiver> receivers = new HashSet<>();

    private final LinkedHashSet<Radar> radars = new LinkedHashSet<>();
    //  Set of all platforms

    private final Set<Platform> platforms = new HashSet<>();

    //  There can be only one of each of these.
    private Document systemsDoc = null;

    private Document platformsDoc = null;

    private Document weaponsDoc = null;

    private Space space = null;

    private static IEarthModel earthModel = new FlatEarth();

    private static ITerrainModel terrainModel = new BaldEarthTerrain();

    private final LOSUtil emLOSUtil = new LOSUtil(this,
        IEarthModel.EarthFactor.EM_EARTH);

    private final LOSUtil realLOSUtil = new LOSUtil(this,
        IEarthModel.EarthFactor.REAL_EARTH);

    private RandomNumberGenerator rng = null;

    private File asi_file = null;

    /** Keeps track of all fonts used to draw objects in the FUSE GUI */
    protected HashMap<Float, Font> size_font_map = new HashMap<>();

    //  Simdis XML inputs
    private Double3D refLLA = null;

    private String gog_file = null;

    private String ded_map = null;

    private String wvs_map = null;

    private String classification_label = null;

    private String classification_color = "green";

    //  We're making this a member variable so that it is accessible via
    //  recursive readScenario calls.
    private final Map<String, ViewFrame> viewFrames =
        new HashMap<>();

    //  Used to ensure that all ID's are unique and to support rapid lookup of
    //  agents by ID (e.g., to make subordinate/superior connections).
    private final HashMap<Integer, CMAgent> agent_ids =
        new HashMap<>();

    private static final Logger logger = LogManager.getLogger(CMWorld.class);

    /**
     * Creates a new instance of CMWorld.
     */
    public CMWorld()
    {
    }

    /**
     * Returns the font of the specified size.
     * @param size point size of the font.
     * @return the font.
     */
    public Font getFont(float size)
    {
        Font font = null;

        if (this.size_font_map.containsKey(size))
        {
            font = this.size_font_map.get(size);
        }
        else
        {
            font = new FontManager().getDefaultFont().deriveFont(size);
            this.size_font_map.put(size, font);
        }

        return font;
    }

    /**
     * Attempts to add the specified ID to the world, mapping it to the specified agent.
     *
     * @param id ID number to add to the world.
     * @param agent agent with the ID.
     * @return true if the ID is unique, false otherwise.
     */
    public boolean addId(int id, CMAgent agent)
    {
        boolean success = false;
        if (!this.agent_ids.containsKey(id))
        {
            this.agent_ids.put(id, agent);
            success = true;
        }
        return success;
    }

    /**
     * Removes the specified ID from the world map of IDs.
     * @param id ID to be removed.
     * @return true if the ID was found in the map and removed, false otherwise.
     */
    public boolean removeId(int id)
    {
        boolean success = false;
        if (this.agent_ids.remove(id) != null)
        {
            success = true;
        }
        return success;
    }

    /**
     * Returns the agent by ID.
     * @param id ID number of agent to retrieve.
     * @return retrieved agent.
     */
    public CMAgent getAgent(int id)
    {
        return this.agent_ids.get(id);
    }

    /**
     * Sets the .asi file for writing SIMDIS data.
     * @param asi_file the SIMDIS .asi file.
     */
    public void setASIFile(File asi_file)
    {
        this.asi_file = asi_file;
    }

    /**
     * Returns the .asi file for writing SIMDIS data.
     * @return SIMDIS .asi file.
     */
    public File getASIFile()
    {
        return this.asi_file;
    }

    /**
     * Returns the map of agent IDs.
     * @return map of agent IDs.
     */
    protected HashMap<Integer, CMAgent> getAgentIds()
    {
        return this.agent_ids;
    }

    /**
     * Adds an aircraft to the world.
     * @param ac an Aircraft object.
     */
    public void addAircraft(Aircraft ac)
    {
        this.aircraft.add(ac);
    }

    /**
     * Returns all aircraft in the world.
     * @return collection of aircraft.
     */
    public HashSet<Aircraft> getAircraft()
    {
        return this.aircraft;
    }

    /**
     * Returns a Map of ViewFrame objects accessed by name.
     *
     * @return Map of ViewFrames.
     */
    public Map<String, ViewFrame> getViewFrames()
    {
        return viewFrames;
    }

    /**
     * Adds a target platform to the world.
     * @param t a Platform object.
     */
    public void addTarget(Platform t)
    {
        this.targets.add(t);
    }

    /**
     * Returns all targets in the world.
     * @return collection of platforms.
     */
    public HashSet<Platform> getTargets()
    {
        return this.targets;
    }

    /**
     * Returns all platforms in the world.
     * @return set of all platforms.
     */
    public Set<Platform> getPlatforms()
    {
        return this.platforms;
    }

    /**
     * Adds a jammer to the world.
     * @param j a Jammer object.
     */
    public void addJammer(Jammer j)
    {
        this.jammers.add(j);
    }

    /**
     * Returns all jammers in the world.
     * @return a collection of jammers.
     */
    public HashSet<Jammer> getJammers()
    {
        return this.jammers;
    }

    /**
     * Adds a live Radar object to the simulation world (NOT to be confused with a
     * radar type, although it uses the same object class).
     * @param r a Radar object.
     */
    public void addRadar(Radar r)
    {
        this.radars.add(r);
    }

    /**
     * Returns all radars in the world.
     * @return collection of radars.
     */
    public LinkedHashSet<Radar> getRadars()
    {
        return this.radars;
    }

    /**
     * Adds a receiver to the world.
     * @param rx a Receiver object.
     */
    public void addReceiver(Receiver rx)
    {
        this.receivers.add(rx);
    }

    /**
     * Returns all receivers in the world.
     * @return collection of receivers.
     */
    public HashSet<Receiver> getReceivers()
    {
        return this.receivers;
    }

    /**
     * Called by the FUSE framework to populate the universe of agents prior to each
     * simulation run.
     * @param universe the universe to be populated with agents.
     */
    @Override
    public void populateUniverse(Universe universe)
    {
        //  Don't do anything separate for jammers, since jammers are
        //  included in the aircraft set.
        for (Aircraft ac : aircraft)
        {
            universe.addAgent(ac);

            if (ac instanceof JammerAircraft)
            {
                Receiver rx = ((JammerAircraft) ac).getReceiver();
                if (rx != null)
                {
                    universe.addAgent(rx);
                }
            }
        }

        for (Platform t : targets)
        {
            universe.addAgent(t);

            if (t instanceof MovingEmitterPlatform)
            {
                Radar r = ((MovingEmitterPlatform) t).getRadar();
                universe.addAgent(r);
            }
            else
            {
                if (t instanceof RadarPlatform)
                {
                    for (Radar r : ((RadarPlatform) t).getRadars())
                    {
                        universe.addAgent(r);
                    }
                }
                else
                {
                    if (t instanceof SAMTEL)
                    {
                        for (Platform p : t.getSubordinates())
                        {
                            if (p instanceof Weapon)
                            {
                                universe.addAgent(p);
                            }
                        }
                    }
                }
            }
        }

        for (Receiver rx : receivers)
        {
            universe.addAgent(rx);
        }

        this.emLOSUtil.reset();
        this.realLOSUtil.reset();
    }

    /**
     * Returns the space for this simulation.
     *
     * @return a FUSE Space object.
     */
    public Space getSpace()
    {
        return this.space;
    }

    /**
     * Returns the random number generator for this simulation.
     *
     * @return RandomNumberGenerator object.
     */
    public RandomNumberGenerator getRNG()
    {
        return this.rng;
    }

    /**
     * Sets the Earth model for this simulation.
     *
     * @param earthModel earth model object.
     */
    static void setEarthModel(IEarthModel earthModel)
    {
        CMWorld.earthModel = earthModel;
    }

    /**
     * Returns the Earth model for this simulation.
     *
     * @return IEarthModel object.
     */
    public static IEarthModel getEarthModel()
    {
        return earthModel;
    }

    /**
     * Returns the terrain model for this simulation.
     *
     * @return ITerrainModel object.
     */
    public static ITerrainModel getTerrainModel()
    {
        return terrainModel;
    }

    /**
     * Sets the terrain model for this simulation.
     *
     * @param terrainModel terrain model object.
     */
    static void setTerrainModel(ITerrainModel terrainModel)
    {
        CMWorld.terrainModel = terrainModel;
    }

    /**
     * Returns the EM line-of-site utilites used by platforms in this simulation.
     *
     * @return LOSUtil object
     */
    public LOSUtil getEMLOSUtil()
    {
        return emLOSUtil;
    }

    /**
     * Returns the true line-of-site utilites used by platforms in this simulation.
     *
     * @return LOSUtil object
     */
    public LOSUtil getRealLOSUtil()
    {
        return realLOSUtil;
    }

    /**
     * Parses the XML node and creates the weapons specified therein.
     * @param node XML node.
     * @return Set of newly created weapons.
     */
    public Set<Weapon> createWeapons(Node node)
    {
        Weapon weapon = null;

        HashSet<Weapon> weapons = new HashSet<Weapon>();

        Node weaponNode = null;

        String dummy_s = "dummy";

        if (node.getNodeName().equalsIgnoreCase("Weapon"))
        {
            //  Then look up the weapon from the weapons db based on its attributes
            //  Get the type attribute, then look for the name amongst the chillens of that type
            weaponNode = this.findWeaponNodeInDB(node);

            if (weaponNode == null)
            {
                //  find the system information specified locally
                weaponNode = this.findWeaponNode(node);
            }
        }

        if (weaponNode != null)
        {
            int num = 1;
            if (node.getAttributes().getNamedItem("num") != null)
            {
                num = Integer.parseInt(node.getAttributes().getNamedItem("num").
                    getTextContent());
            }

            for (int i = 0; i < num; i++)
            {
                if (weaponNode.getNodeName().equalsIgnoreCase("SAM"))
                {
                    weapon = new SAM(dummy_s, this);
                    weapons.add(weapon);
                    weapon.fromXML(weaponNode);

//                    //  Fix name
//                    if ( num > 1 )
//                    {
//                        String newname = weapon.getName()+"_"+String.valueOf(i);
//                        weapon.setName(newname);
//                    }
                }

                this.platforms.add(weapon);

                //  Any child overrides?
                Node child = this.findWeaponNode(node);
                if (child != null && weapon != null && child != weaponNode)
                {
                    weapon.fromXML(child);
                }
            }
        }

        return weapons;
    }

    /**
     * Creates XML nodes containing the weapon's attributes.
     *
     * @param weapon Weapon object to be written.
     * @param node root XML node within which to write the weapon.
     */
    public void writeWeaponToXML(Weapon weapon, Node node)
    {
        Document document = node.getOwnerDocument();
        Element e = document.createElement("Weapon");
        node.appendChild(e);

        Element f = document.createElement(weapon.getClass().getSimpleName());
        e.appendChild(f);

        weapon.toXML(f);
    }

    /**
     * Finds the weapons node specified by the XML node within the
     * weapons database and returns it (or null if not found).
     * @param node XML node.
     * @return weapons node (XML).
     */
    protected Node findWeaponNodeInDB(Node node)
    {
        Node weaponNode = null;

        String name_s = "name";

        if (node.getAttributes().getNamedItem(name_s) != null)
        {
            String weaponName = node.getAttributes().getNamedItem(name_s).
                getTextContent();

            //  Then find the system information in the systems db
            NodeList names = this.weaponsDoc.getElementsByTagName(name_s);

            //  Now lookup the system name in the node list and instantiate
            for (int i = 0; i < names.getLength(); i++)
            {
                String name = names.item(i).getTextContent();

                if (name.equals(weaponName))
                {
                    weaponNode = names.item(i).getParentNode();
                    break;
                }
            }
        }

        return weaponNode;
    }

    /**
     * Finds the weapons node within the specified XML node within the main scenario document.
     * @param node XML node.
     * @return weapons node (XML).
     */
    protected Node findWeaponNode(Node node)
    {
        Node weaponNode = null;

        NodeList chillens = node.getChildNodes();
        for (int i = 0; i < chillens.getLength(); i++)
        {
            Node child = chillens.item(i);
            if (child.getNodeName().equalsIgnoreCase("SAM"))
            {
                weaponNode = child;
                break;
            }
            else
            {
                weaponNode = this.findSystemNode(child);
            }
        }

        return weaponNode;
    }

    /**
     * Creates and returns the MovePlatformBehavior specified by the XML node.  The
     * behavior may be one of the following:
     * <ul>
     * <li>TwoWayRoutePointsBehavior</li>
     * <li>OneWayRoutePointsBehavior</li>
     * <li>PeriodicRoutePointsBehavior</li>
     * <li>SAMFlyoutBehavior</li>
     * </ul>
     *
     *
     * @param node contains the behavior data in an XML node
     * @param platform the platform to attach the behavior to.
     * @return the MovePlatformBehavior that was created, or null if no behavior
     * could be created.
     */
    public MovePlatformBehavior createMovePlatformBehavior(Node node,
        MobilePlatform platform)
    {
        MovePlatformBehavior behavior = null;
        if (node.getNodeName().equalsIgnoreCase("MovePlatformBehavior"))
        {
            String behavior_type = node.getAttributes().getNamedItem("type").
                getTextContent();

            if (behavior_type.equalsIgnoreCase("TwoWayRoutePointsBehavior") &&
                platform instanceof Aircraft)
            {
                behavior = new TwoWayRoutePointsBehavior((Aircraft) platform);
            }
            else
            {
                if (behavior_type.equalsIgnoreCase("OneWayRoutePointsBehavior"))
                {
                    behavior = new OneWayRoutePointsBehavior(platform);
                }
                else
                {
                    if (behavior_type.equalsIgnoreCase(
                        "PeriodicRoutePointsBehavior"))
                    {
                        behavior = new PeriodicRoutePointsBehavior(platform);
                    }
                    else
                    {
                        if (behavior_type.equalsIgnoreCase(
                            "BankedPeriodicRoutePointsBehavior"))
                        {
                            behavior =
                                new BankedPeriodicRoutePointsBehavior(platform);
                        }
                        else
                        {
                            if (behavior_type.equalsIgnoreCase(
                                "SAMFlyoutBehavior") &&
                                platform instanceof SAM)
                            {
                                behavior = new SAMFlyoutBehavior((SAM) platform);
                            }
                        }
                    }
                }
            }

            if (behavior != null)
            {
                behavior.fromXML(node);
            }
        }

        return behavior;
    }

    /**
     * Creates XML nodes containing the behavior's attributes.
     *
     * @param behavior MovePlatformBehavior object to be written.
     * @param node root XML node within which to write the behavior.
     */
    public void writeMovePlatformBehaviorToXML(MovePlatformBehavior behavior,
        Node node)
    {
        Document document = node.getOwnerDocument();

        Element e = document.createElement("MovePlatformBehavior");
        e.setAttribute("type", behavior.getClass().getSimpleName());
        node.appendChild(e);

        behavior.toXML(e);
    }

    /**
     * Creates the system specified by the given Node. The system may be one of
     * the following:
     * <ul>
     * <li>Radar</li>
     * <li>DirectionalRadar</li>
     * <li>CookieCutterReceiver</li>
     * <li>SensitivityReceiver</li>
     * <li>TableLookupReceiver</li>
     * <li>ThreeLevelTableLookupReceiver</li>
     * <li>BasicJammer</li>
     * <li>AssignmentsJammer</li>
     * </ul>
     *
     * @param node contains the system data in an XML node
     * @return the system that was created, or null if no system could be
     * created for the given node.
     */
    public CMSystem createSystem(Node node)
    {
        Node systemsNode = null;

        String dummy_s = "dummy";

        if (node.getNodeName().equalsIgnoreCase("System"))
        {
            //  Then look up the system from the systems db based on its attributes
            //  Get the type attribute, then look for the name amongst the chillens of that type
            systemsNode = this.findSystemNodeInDB(node);

            if (systemsNode == null)
            {
                //  find the system information specified locally
                systemsNode = this.findSystemNode(node);
            }
        }

        CMSystem system = null;
        if (systemsNode != null)
        {
            if (systemsNode.getNodeName().equalsIgnoreCase("Radar"))
            {
                system = new Radar(dummy_s, this);
                system.fromXML(systemsNode);
            }
            else
            {
                if (systemsNode.getNodeName().equalsIgnoreCase(
                    "DirectionalRadar"))
                {
                    system = new DirectionalRadar(dummy_s, this);
                    system.fromXML(systemsNode);
                }
                else
                {
                    if (systemsNode.getNodeName().equalsIgnoreCase(
                        "CookieCutterReceiver"))
                    {
                        system = new CookieCutterReceiver(dummy_s, this);
                        system.fromXML(systemsNode);
                    }
                    else
                    {
                        if (systemsNode.getNodeName().equalsIgnoreCase(
                            "SensitivityReceiver"))
                        {
                            system = new SensitivityReceiver(dummy_s, this);
                            system.fromXML(systemsNode);
                        }
                        else
                        {
                            if (systemsNode.getNodeName().equalsIgnoreCase(
                                "TableLookupReceiver"))
                            {
                                system = new TableLookupReceiver(dummy_s, this);
                                system.fromXML(systemsNode);
                            }
                            else
                            {
                                if (systemsNode.getNodeName().equalsIgnoreCase(
                                    "ThreeLevelTableLookupReceiver"))
                                {
                                    system =
                                        new ThreeLevelTableLookupReceiver(
                                        dummy_s,
                                        this);
                                    system.fromXML(systemsNode);
                                }
                                else
                                {
                                    if (systemsNode.getNodeName().
                                        equalsIgnoreCase("BasicJammer"))
                                    {
                                        system = new BasicJammer(dummy_s, this,
                                            Double.MIN_VALUE, Double.MIN_VALUE,
                                            Double.MIN_VALUE);
                                        system.fromXML(systemsNode);
                                    }
                                    else
                                    {
                                        if (systemsNode.getNodeName().
                                            equalsIgnoreCase("AssignmentsJammer"))
                                        {
                                            system =
                                                new AssignmentsJammer(dummy_s,
                                                this);
                                            system.fromXML(systemsNode);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            //  Any child overrides?
            Node child = this.findSystemNode(node);
            if (child != null && system != null && child != systemsNode)
            {
                system.fromXML(child);
            }
        }
        return system;
    }

    /**
     * Creates XML nodes containing the system's attributes.
     *
     * @param system CMSystem object to be written.
     * @param node root XML node within which to write the system.
     */
    public void writeSystemToXML(CMSystem system, Node node)
    {
        Document document = node.getOwnerDocument();

        Element e = document.createElement("System");
        node.appendChild(e);

        //  Now create child tag identifying system type
        Element f = document.createElement(system.getClass().getSimpleName());
        e.appendChild(f);

        system.toXML(f);
    }

    /**
     * Creates the route point specified by the given Node. In CHECKMATE, the only route point is
     * RoutePoint, but dependent packages may have extensions that require over-riding this method.
     *
     * @param node contains the route point data in an XML node
     * @return the route point that was created, or null if no route point could be
     * created for the given node.
     */
    public RoutePoint createRoutePoint(Node node)
    {
        Node rpNode = null;
        RoutePoint rp = null;

        if (node.getNodeName().equalsIgnoreCase("route-point"))
        {
            rpNode = node.getAttributes().getNamedItem("type");

            String pointType = "RoutePoint";
            if (rpNode != null)
            {
                pointType = rpNode.getTextContent();
            }

            if (pointType.equalsIgnoreCase("RoutePoint"))
            {
                rp = new RoutePoint();
                rp.fromXML(node);
            }
            else
            {
                if (pointType.equalsIgnoreCase("BankedRoutePoint"))
                {
                    rp = new BankedRoutePoint();
                    rp.fromXML(node);
                }
            }
        }
        return rp;
    }

    /**
     * Creates XML nodes containing the route point's attributes.
     *
     * @param rp RoutePoint object to be written.
     * @param node root XML node within which to write the route point.
     */
    public void writeRoutePointToXML(RoutePoint rp, Node node)
    {
        Document document = node.getOwnerDocument();

        Element e = document.createElement("route-point");
        e.setAttribute("type", rp.getClass().getSimpleName());
        node.appendChild(e);

        rp.toXML(e);
    }

    /**
     * This method takes a "System" tag and looks for the system in the
     * systems DB.  This method should not need to be overridden unless an entire
     * new systems type is added (i.e., not Radar, Receiver, or Jammer).
     *
     * @param node System node from the scenario
     * @return node in systems DB pointing to the system
     */
    protected Node findSystemNodeInDB(Node node)
    {
        Node systemsNode = null;

        String type_s = "type";
        String name_s = "name";

        if (node.getAttributes().getNamedItem(type_s) != null)
        {
            String systemType = "";
            if (node.getAttributes().getNamedItem(type_s).getTextContent().
                equalsIgnoreCase("Radar"))
            {
                systemType = "Radars";
            }
            else
            {
                if (node.getAttributes().getNamedItem(type_s).getTextContent().
                    equalsIgnoreCase("Receiver"))
                {
                    systemType = "Receivers";
                }
                else
                {
                    if (node.getAttributes().getNamedItem(type_s).getTextContent().
                        equalsIgnoreCase("Jammer"))
                    {
                        systemType = "Jammers";
                    }
                }
            }

            String systemName = node.getAttributes().getNamedItem(name_s).
                getTextContent();

            //  Then find the system information in the systems db
            NodeList names = this.systemsDoc.getElementsByTagName(name_s);

            //  Now lookup the system name in the node list and instantiate
            for (int i = 0; i < names.getLength(); i++)
            {
                String name = names.item(i).getTextContent();

                if (name.equals(systemName) && names.item(i).getParentNode().
                    getParentNode().getNodeName().equals(systemType))
                {
                    systemsNode = names.item(i).getParentNode();
                    break;
                }
            }
        }

        return systemsNode;
    }

    /**
     * Given a node, searches for a child node that contains a system tag.
     * @param node starting node for the search.
     * @return node containing system.
     */
    protected Node findSystemNode(Node node)
    {
        Node systemNode = null;

        NodeList chillens = node.getChildNodes();
        for (int i = 0; i < chillens.getLength(); i++)
        {
            Node child = chillens.item(i);
            if (child.getNodeName().equalsIgnoreCase("Radar") ||
                child.getNodeName().equalsIgnoreCase("DirectionalRadar") ||
                child.getNodeName().equalsIgnoreCase("CookieCutterReceiver") ||
                child.getNodeName().equalsIgnoreCase("SensitivityReceiver") ||
                child.getNodeName().equalsIgnoreCase("TableLookupReceiver") ||
                child.getNodeName().equalsIgnoreCase(
                "ThreeLevelTableLookupReceiver") ||
                child.getNodeName().equalsIgnoreCase("BasicJammer") ||
                child.getNodeName().equalsIgnoreCase("AssignmentsJammer"))
            {
                systemNode = child;
                break;
            }
            else
            {
                systemNode = this.findSystemNode(child);
            }
        }

        return systemNode;
    }

    /**
     *  Given the node, finds the appropriate type of platform to create and
     *  reads in the params specified.
     * @param node node containing the platform description.
     * @return a newly created platform object.
     */
    protected Platform createPlatform(Node node)
    {
        //  Platform types handled:
        //  Platform
        //  EarlyWarningSite extends UncertainLocationPlatform
        //  SAMSite extends TargetAssignmentC2
        //  SAMBattalion extends TargetAssignmentC2
        //  RadarPlatform extends UncertainLocationPlatform
        //  SAMTEL extends UncertainLocationPlatform
        //  UncertainLocationPlatform extends Platform
        //  MovingEmitterPlatform extends MobilePlatform (abstract)
        //  Aircraft extends MobilePlatform
        //  JammerAircraft exents Aircraft
        //  PassThroughC2 extends UncertainLocationPlatform
        //  TargetAssignmentC2 extends PassThroughC2

        String dummy_s = "dummy";
        Platform platform = null;

        Font font = getFont(10.0f);

        if (node.getNodeName().equalsIgnoreCase("Platform"))
        {
            platform = new Platform(dummy_s, this, Integer.MIN_VALUE, font,
                dummy_s, null);
        }
        else
        {
            if (node.getNodeName().equalsIgnoreCase("EarlyWarningSite"))
            {
                platform = new EarlyWarningSite(dummy_s, this, Integer.MIN_VALUE,
                    font, dummy_s, null);
            }
            else
            {
                if (node.getNodeName().equalsIgnoreCase("SAMSite"))
                {
                    platform = new SAMSite(dummy_s, this, Integer.MIN_VALUE,
                        font, dummy_s, null);
                }
                else
                {
                    if (node.getNodeName().equalsIgnoreCase("SAMBattalion"))
                    {
                        platform = new SAMBattalion(dummy_s, this,
                            Integer.MIN_VALUE, font, dummy_s, null);
                    }
                    else
                    {
                        if (node.getNodeName().equalsIgnoreCase("PassThroughC2"))
                        {
                            platform = new PassThroughC2(dummy_s, this,
                                Integer.MIN_VALUE, font,
                                dummy_s, null);
                        }
                        else
                        {
                            if (node.getNodeName().equalsIgnoreCase(
                                "TargetAssignmentC2"))
                            {
                                platform = new TargetAssignmentC2(dummy_s, this,
                                    Integer.MIN_VALUE,
                                    font, dummy_s, null);
                            }
                            else
                            {
                                if (node.getNodeName().equalsIgnoreCase("SAMTEL"))
                                {
                                    platform = new SAMTEL(dummy_s, this,
                                        Integer.MIN_VALUE, font,
                                        dummy_s, null);
                                }
                                else
                                {
                                    if (node.getNodeName().equalsIgnoreCase(
                                        "RadarPlatform"))
                                    {
                                        platform = new RadarPlatform(dummy_s,
                                            this, Integer.MIN_VALUE, font,
                                            dummy_s, null);
                                    }
                                    else
                                    {
                                        if (node.getNodeName().equalsIgnoreCase(
                                            "WeaponsPlatform"))
                                        {
                                            platform = new SAMTEL(dummy_s, this,
                                                Integer.MIN_VALUE, font,
                                                dummy_s, null);
                                        }
                                        else
                                        {
                                            if (node.getNodeName().
                                                equalsIgnoreCase(
                                                "MovingEmitterPlatform"))
                                            {
                                                platform = new MovingEmitterPlatform(dummy_s, this,
                                                    Integer.MIN_VALUE, font,
                                                    dummy_s, null);
                                            }
                                            else
                                            {
                                                if (node.getNodeName().
                                                    equalsIgnoreCase("Aircraft"))
                                                {
                                                    platform = new Aircraft(
                                                        dummy_s, this,
                                                        Integer.MIN_VALUE, font,
                                                        dummy_s, null);
                                                }
                                                else
                                                {
                                                    if (node.getNodeName().
                                                        equalsIgnoreCase(
                                                        "JammerAircraft"))
                                                    {
                                                        platform =
                                                            new JammerAircraft(
                                                            dummy_s, this,
                                                            Integer.MIN_VALUE,
                                                            font,
                                                            dummy_s, null);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (platform != null)
        {
            platform.fromXML(node);
        }

        return platform;
    }

    /**
     * Returns the XML document containing the systems database.
     *
     * @return systems XML document.
     */
    protected Document getSystemsDocument()
    {
        return this.systemsDoc;
    }

    /**
     * Returns the weapons XML document.
     * @return XML document.
     */
    protected Document getWeaponsDocument()
    {
        return this.weaponsDoc;
    }

    /**
     * Scans through the specified Platforms-DB document looking for the
     * platform with the given name. If a platform is found with that name it
     * is intantiated and returned.
     *
     * @param platformName the name of the platform to parse out of the
     * {@code platformsDoc}
     * @return the platform that was parsed out of the {@code platformDoc} or
     * null if no platform with the given name could be found or parsed
     */
    protected Platform createPlatform(String platformName)
    {
        Platform platform = null;

        NodeList names = this.platformsDoc.getElementsByTagName("name");
        Node targetPlatformRootNode = null;
        for (int i = 0; i < names.getLength(); i++)
        {
            if (names.item(i).getTextContent().equalsIgnoreCase(platformName))
            {
                assert (targetPlatformRootNode == null) : "Duplicate name: " +
                    platformName;
                targetPlatformRootNode = names.item(i).getParentNode();
            }
        }

        if (targetPlatformRootNode != null)
        {
            platform = this.createPlatform(targetPlatformRootNode);
        }

        return platform;
    }

    /**
     * Given a node, searches for a child node that contains a platform tag
     * @param node root node within which to search.
     * @return node containing a platform.
     */
    protected Node findPlatformNode(Node node)
    {
        Node platformNode = null;

        //  Find a platform node anywhere in here.
        NodeList chillens = node.getChildNodes();
        for (int i = 0; i < chillens.getLength(); i++)
        {
            Node child = chillens.item(i);

            if (child.getNodeName().equalsIgnoreCase("Platform") ||
                child.getNodeName().equalsIgnoreCase("EarlyWarningSite") ||
                child.getNodeName().equalsIgnoreCase("SAMSite") ||
                child.getNodeName().equalsIgnoreCase("SAMBattalion") ||
                child.getNodeName().equalsIgnoreCase("SAMTEL") ||
                child.getNodeName().equalsIgnoreCase("RadarPlatform") ||
                child.getNodeName().equalsIgnoreCase("WeaponsPlatform") ||
                child.getNodeName().equalsIgnoreCase("MovingEmitterPlatform") ||
                child.getNodeName().equalsIgnoreCase("JammerAircraft") ||
                child.getNodeName().equalsIgnoreCase("Aircraft") ||
                child.getNodeName().equalsIgnoreCase("PassThroughC2") ||
                child.getNodeName().equalsIgnoreCase("TargetAssignmentC2"))
            {
                platformNode = child;
                break;
            }
            else
            {
                platformNode = this.findPlatformNode(child);
            }
        }

        return platformNode;
    }

    /**
     * Reads the scenario defined by the passed in XML filename.
     *
     * @param scenario FUSE Scenario object into which the scenario will
     * be read.
     * @param scenarioFileName the name of the XML file containing the
     * scenario.
     */
    public void readScenario(String scenarioFileName, Scenario scenario)
    {
        //  We'll be using this local variable over and over.
        NodeList mainNodeList;

        URL scenarioURL = null;
        try
        {
            scenarioURL = new URL(scenarioFileName);
        }
        catch (Exception ex)
        {
        }

        InputStream scenarioIS = null;
        try
        {
            scenarioIS = scenarioURL.openStream();
        }
        catch (Exception e)
        {
        }

        Document scenarioDoc = getDocument(scenarioIS, "Scenario");

        String path = scenarioURL.getPath().substring(0, scenarioURL.getPath().
            lastIndexOf("/") + 1);

        //  Find and open a systems db if one exists and one is not already open
        if (this.systemsDoc == null)
        {
            mainNodeList = scenarioDoc.getElementsByTagName("Systems-DB");
            assert (mainNodeList.getLength() == 1) :
                "Only 1 system-DB file at a time is currently supported";
            String systemDocFilename = path + mainNodeList.item(0).
                getTextContent();
            InputStream systemsIS = null;
            try
            {
                systemsIS = new FileInputStream(new File(systemDocFilename));
            }
            catch (FileNotFoundException ex)
            {
            }
            systemsDoc = getDocument(systemsIS, "Systems-DB");
        }

        //  Find and open a platforms db if one exists and one is not already open
        if (this.platformsDoc == null)
        {
            mainNodeList = scenarioDoc.getElementsByTagName("Platforms-DB");
            assert (mainNodeList.getLength() == 1) :
                "Only 1 Platforms-DB file at a time is currently supported";
            String platformDocFilename = path + mainNodeList.item(0).
                getTextContent();
            InputStream platformsIS = null;
            try
            {
                platformsIS = new FileInputStream(new File(platformDocFilename));
            }
            catch (FileNotFoundException ex)
            {
            }
            platformsDoc = getDocument(platformsIS, "Platforms-DB");
        }

        if (this.weaponsDoc == null)
        {
            mainNodeList = scenarioDoc.getElementsByTagName("Weapons-DB");
            if (mainNodeList.getLength() == 1)
            {
                String weaponsDocName = path + mainNodeList.item(0).
                    getTextContent();
                InputStream weaponsIS = null;
                try
                {
                    weaponsIS = new FileInputStream(new File(weaponsDocName));
                }
                catch (FileNotFoundException ex)
                {
                }
                weaponsDoc = getDocument(weaponsIS, "Weapons-DB");
            }
        }

        //
        //  Now create the Universe, space, and any views
        //

        Universe universe = scenario.getUniverse();

        if (universe == null)
        {
            NodeList universeNodes =
                scenarioDoc.getElementsByTagName("Universe");
            assert (universeNodes.getLength() == 1);
            Node universeNode = universeNodes.item(0);
            assert (universeNode.getAttributes().getLength() == 1);
            String universeClass = universeNode.getAttributes().item(0).
                getTextContent();

            //  Parse space and views (if any)
            NodeList children = universeNode.getChildNodes();
            for (int c = 0; c < children.getLength(); c++)
            {
                Node child = children.item(c);
                Node dted = null;
                if (child.getNodeName().equalsIgnoreCase("Space"))
                {
                    assert (space == null) : "Two spaces in 1 universe?";
                    space = parseSpace(child);
                }
                else if (child.getNodeName().equalsIgnoreCase("View"))
                {
                    ViewFrame viewFrame = parseView(child);
                    viewFrames.put(viewFrame.getName(), viewFrame);
                }
                else if (child.getNodeName().equalsIgnoreCase(
                    "RandomNumberGenerator"))
                {
                    String rngClass = child.getAttributes().getNamedItem("class").
                        getTextContent();

                    if (rngClass.equals("MersenneTwisterFast"))
                    {
                        this.rng = MersenneTwisterFast.getInstance();
                    }
                    else
                    {
                        if (rngClass.equals("MersenneTwister"))
                        {
                            this.rng = MersenneTwister.getInstance();
                        }
                    }
                }
                else if (child.getNodeName().equalsIgnoreCase(
                    "EarthModel"))
                {
                    if (child.getTextContent().equalsIgnoreCase(
                        "FLAT"))
                    {
                        earthModel = new FlatEarth();
                    }
                    else
                    {
                        if (child.getTextContent().equalsIgnoreCase(
                            "ROUND"))
                        {
                            earthModel = new RoundEarth();
                        }
                    }
                }
                else if (child.getNodeName().equalsIgnoreCase("TerrainModel"))
                {
                    if (child.getTextContent().equalsIgnoreCase(
                        "Bald"))
                    {
                        terrainModel = new BaldEarthTerrain();
                    }
                    else if (child.getTextContent().
                        equalsIgnoreCase("TwoLevel"))
                    {
                        terrainModel = new TwoLevelTerrain();
                        if (dted != null)
                        {
                            ((TwoLevelTerrain) terrainModel).fromXML(child);
                        }
                    }
                }
                else if (child.getNodeName().equalsIgnoreCase("DTED"))
                {
                    if (terrainModel instanceof TwoLevelTerrain)
                    {

                        ((TwoLevelTerrain) terrainModel).fromXML(child);
                    }
                    else
                    {
                        dted = child;
                    }
                }
            }

            if (this.space == null)
            {
                space = new Cartesian2DSpace(100.,
                    100.);
            }

            if (this.rng == null)
            {
                this.rng = MersenneTwisterFast.getInstance();
            }



            if (universeClass.equalsIgnoreCase(
                "GUIUniverse"))
            {
                GUIUniverse guiUniverse = new GUIUniverse(space);

                for (ViewFrame vf : viewFrames.values())
                {
                    guiUniverse.addView(vf);
                }

                universe = guiUniverse;
            }
            else
            {
                if (universeClass.equalsIgnoreCase("SimpleUniverse"))
                {
                    SimpleUniverse simpleUniverse =
                        new SimpleUniverse(space);
                    universe =
                        simpleUniverse;
                }
                else
                {
                    throw new IllegalArgumentException("Unrecognized universe: " +
                        universeNode.getNodeName());
                }

            }

            scenario.setUniverse(universe);

            universe.setDefaultRandomNumberGenerator(this.rng);
        }

        NodeList simdisNodes = scenarioDoc.getElementsByTagName("Simdis");

        if (simdisNodes.getLength() > 0)
        {
            Node simdisNode = simdisNodes.item(0);
            //  chillens
            NodeList simdischildren = simdisNode.getChildNodes();
            for (int c = 0; c <
                simdischildren.getLength(); c++)
            {
                Node child = simdischildren.item(c);
                if (child.getNodeName().equalsIgnoreCase("RefLLA"))
                {
                    double latitude = Double.parseDouble(child.getAttributes().
                        getNamedItem("lat").getTextContent());
                    double longitude = Double.parseDouble(child.getAttributes().
                        getNamedItem("long").getTextContent());
                    double altitude = Double.parseDouble(child.getAttributes().
                        getNamedItem("alt").getTextContent());
                    this.refLLA =
                        new Double3D(latitude, longitude, altitude);
                }
                else
                {
                    if (child.getNodeName().equalsIgnoreCase("GOGFile"))
                    {
                        this.gog_file = child.getTextContent();
                    }
                    else
                    {
                        if (child.getNodeName().equalsIgnoreCase("DEDMap"))
                        {
                            this.ded_map = child.getTextContent();
                        }
                        else
                        {
                            if (child.getNodeName().equalsIgnoreCase(
                                "WVSMap"))
                            {
                                this.wvs_map = child.getTextContent();
                            }
                            else
                            {
                                if (child.getNodeName().equalsIgnoreCase(
                                    "Classification"))
                                {
                                    this.classification_label =
                                        child.getAttributes().
                                        getNamedItem("label").getTextContent();
                                    if (child.getAttributes().getNamedItem(
                                        "color") !=
                                        null)
                                    {
                                        this.classification_color =
                                            child.getAttributes().
                                            getNamedItem("color").
                                            getTextContent();
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }

        //
        //  Start time and end time.
        //
        mainNodeList = scenarioDoc.getElementsByTagName(
            "simulation-start-time");

        if (mainNodeList.getLength() > 0)
        {
            scenario.setStartTime(Double.parseDouble(mainNodeList.item(0).
                getTextContent()));
        }

        mainNodeList = scenarioDoc.getElementsByTagName(
            "simulation-end-time");

        if (mainNodeList.getLength() > 0)
        {
            scenario.setEndTime(Double.parseDouble(mainNodeList.item(0).
                getTextContent()));
        }

//  Now look for recursive Sub-scenario calls
        mainNodeList = scenarioDoc.getElementsByTagName("Sub-Scenario");

        for (int i = 0;
            i < mainNodeList.getLength();
            i++)
        {
            Node snode = mainNodeList.item(i);

            String file_name = "file:" +
                path + snode.getTextContent();

            this.readScenario(file_name, scenario);
        }

//
//  We should now be ready to parse the Players, instantiating platforms and systems by reference
//  from platforms and systems db's, or by direct specification here.
//
//  Procedure:
//  1) Get all player nodes
//  2) Loop over each player
//  3) If a Platform is specified, call createPlatform, passing the name of the platform
//  4) If a System is specified, call createSystem, passing the name and type of system
//  5) If no platform is specified, then find child nodes indicating type of platform.  Instantiate and pass childe nodes.
//  6) Once platform is instantiated, if other parameters are specified (e.g., Route, start-time), then pass these as children to the
//     platform for parsing.
//
        mainNodeList = scenarioDoc.getElementsByTagName("Player");

        for (int i = 0;
            i < mainNodeList.getLength();
            i++)
        {
            Node pnode = mainNodeList.item(i);
            int num_players = 1;
            if (pnode.getAttributes().getNamedItem("num") != null)
            {
                num_players = Integer.parseInt(pnode.getAttributes().
                    getNamedItem("num").getTextContent());
            }

            for (int j = 0; j <
                num_players; j++)
            {
                Platform platform = null;
                if (pnode.getAttributes().getNamedItem("platform") != null)
                {
                    String pname = pnode.getAttributes().getNamedItem(
                        "platform").
                        getTextContent();

                    platform =
                        this.createPlatform(pname);
                }

                if (platform != null)
                {
                    //
                    //  The platform was specified in the platforms DB.  Now let's
                    //  look for other attributes locally to override those created
                    //  based on the DB spec.
                    //
                    Node params = this.findPlatformNode(pnode);

                    if (params != null && params.getNodeName().
                        equalsIgnoreCase(platform.getClass().
                        getSimpleName()))
                    {
                        platform.fromXML(params);
                    }

                }
                else
                {
                    //
                    //  The platform was NOT specified in the platforms DB.  We must
                    //  find the platform specification locally and create it.
                    //
                    Node params = this.findPlatformNode(pnode);

                    if (params == null)
                    {
                        logger.error("Platform node not found within: " +
                            pnode.getNodeName());
                        System.exit(1);
                    }

                    platform = this.createPlatform(params);
                }

                this.platforms.add(platform);

                //  Fix name
                if (num_players > 1)
                {
                    String newname = platform.getName() + j;
                    platform.setName(newname);
                }

//  Other params...look for views
                NodeList chillens = pnode.getChildNodes();
                for (int k = 0; k <
                    chillens.getLength(); k++)
                {
                    Node v = chillens.item(k);
                    if (v.getNodeName().equalsIgnoreCase("paintable-view"))
                    {
                        addPlatformToView(platform, v.getTextContent());
                    }

                }

                //  Now add Platforms to the appropriate container
                //  Systems are added to their containers by populateUniverse
                if (platform instanceof EarlyWarningSite ||
                    platform instanceof MovingEmitterPlatform ||
                    platform instanceof SAMSite ||
                    platform instanceof SAMBattalion ||
                    platform instanceof RadarPlatform ||
                    platform instanceof SAMTEL)
                {
                    targets.add(platform);
                }
                else
                {
                    if (platform instanceof Aircraft)
                    {
                        this.aircraft.add((Aircraft) platform);
                    }

                }
            }
        }

        //  All players are now instantiated.  Need to loop back through and make
        //  the superior/subordinate connections.  This bit of code may need
        //  to be replicated in subclasses in order to make connections for things
        //  not in the targets set.
        for (Platform t : platforms)
        {
            //  See if there is a non-negative superior_id
            if (t.getSuperiorId() >= 0)
            {
                //  Find the superior and make the connection.
                Platform superior =
                    (Platform) this.agent_ids.get(t.getSuperiorId());

                t.setSuperior(superior);
                superior.addSubordinate(t);
            }
        }
    }

    /**
     * Adds agents to viewframes.
     *
     * @param platform Platform to be added to the view.
     * @param view_frame name of view frame to add the platform to.
     */
    protected void addPlatformToView(Platform platform, String view_frame)
    {
        viewFrames.get(view_frame).addPaintable((Paintable) platform);
        platform.addPaintableView(view_frame);

        //  Add radars to views
        if (platform instanceof RadarPlatform)
        {
            RadarPlatform rp = (RadarPlatform) platform;
            for (Radar r : rp.getRadars())
            {
                viewFrames.get(view_frame).addPaintable((Paintable) r);
            }

        }
        else
        {
            if (platform instanceof MovingEmitterPlatform)
            {
                Radar r = ((MovingEmitterPlatform) platform).getRadar();

                viewFrames.get(view_frame).addPaintable((Paintable) r);
            }

        }

        //  Add weapons to views
        if (platform instanceof SAMTEL)
        {
            for (Platform p : platform.getSubordinates())
            {
                if (p instanceof Weapon)
                {
                    viewFrames.get(view_frame).addPaintable((Paintable) p);
                }

            }
        }
    }

    /**
     * Parses a XML node containing a double2D in the form
     * {@code &lt;point2D x="50." y="50."/&gt;}
     *
     * @param point2DNode xml node containing the point2D
     * @return a {@link Double2D} object corresponding to the
     * passed in {@code point2DNode} xml node.
     */
    public static Double2D parsePoint2DNode(
        Node point2DNode)
    {
        assert (point2DNode.getNodeName().equalsIgnoreCase("point2D"));
        double x = Double.parseDouble(point2DNode.getAttributes().getNamedItem(
            "x").
            getTextContent());
        double y = Double.parseDouble(point2DNode.getAttributes().getNamedItem(
            "y").
            getTextContent());
        return new Double2D(x, y);
    }

    /**
     * Creates an XML node with the attributes of the point, appending the node
     * as a child of the parent.
     * @param point Double2D object containing the point.
     * @param parent parent node.
     */
    public static void createPoint2DNode(Double2D point, Node parent)
    {
        Document document = parent.getOwnerDocument();

        Element point2d = document.createElement("point2D");

        point2d.setAttribute("x", String.valueOf(point.getX()));

        point2d.setAttribute("y", String.valueOf(point.getY()));

        parent.appendChild(point2d);
    }

    /**
     * Parses a XML node containing a double2D in the form
     * {@code &lt;point3D x="50." y="50." z="50."/&gt;}
     * where x/y are in nautical miles and z is in feet.
     *
     * @param point3DNode xml node containing the point2D
     * @return a {@link Double3D} object corresponding to the
     * passed in {@code point3DNode} xml node.
     */
    public static Double3D parsePoint3DNode(
        Node point3DNode)
    {
        assert (point3DNode.getNodeName().equalsIgnoreCase("point3D"));
        double x = Double.parseDouble(point3DNode.getAttributes().getNamedItem(
            "x").
            getTextContent());
        double y = Double.parseDouble(point3DNode.getAttributes().getNamedItem(
            "y").
            getTextContent());
        double z = Double.parseDouble(point3DNode.getAttributes().getNamedItem(
            "z").
            getTextContent());
        return new Double3D(x, y, z);
    }

    /**
     * Creates an XML node with the attributes of the point, appending the node
     * as a child of the parent.
     *
     * @param point Double3D object containing the point.
     * @param parent parent node.
     * @return the XML element encoding the point3D object.
     */
    public static Element createPoint3DNode(
        Double3D point, Node parent)
    {
        Document document = parent.getOwnerDocument();

        Element point3d = document.createElement("point3D");

        point3d.setAttribute("x", String.valueOf(point.getX()));

        point3d.setAttribute("y", String.valueOf(point.getY()));

        point3d.setAttribute("z", String.valueOf(point.getZ()));

        parent.appendChild(point3d);

        return point3d;
    }

    /**
     * Parses the following spaces:
     * <ul>
     * <li> Cartesian2DSpace </li>
     * </ul>
     *
     * @param spaceNode the xml node containing the space object to parse
     * @return a {@code Space} object corresponding to the parsed
     * {@code spaceNode}
     */
    private Space parseSpace(Node spaceNode)
    {
        assert (spaceNode.getNodeName().equalsIgnoreCase("space"));
        Space sp = null;
        String spaceClass = spaceNode.getAttributes().getNamedItem("class").
            getTextContent();
        Node unitsNode = spaceNode.getAttributes().getNamedItem("units");
        String units = "";
        if (unitsNode != null)
        {
            units = unitsNode.getTextContent();
        }

        if (spaceClass.equals("Cartesian2DSpace"))
        {
            double xMin = Double.MIN_VALUE, yMin = Double.MIN_VALUE,
                xMax = Double.MIN_VALUE, yMax = Double.MIN_VALUE;
            NodeList children = spaceNode.getChildNodes();
            for (int c = 0; c <
                children.getLength(); c++)
            {
                Node child = children.item(c);
                if (child.getNodeName().equalsIgnoreCase("h-dim"))
                {
                    xMin = Double.parseDouble(child.getAttributes().getNamedItem("min").
                        getTextContent());
                    xMax =
                        Double.parseDouble(child.getAttributes().getNamedItem(
                        "max").
                        getTextContent());
                }

                if (child.getNodeName().equalsIgnoreCase("v-dim"))
                {
                    yMin = Double.parseDouble(child.getAttributes().getNamedItem("min").
                        getTextContent());
                    yMax =
                        Double.parseDouble(child.getAttributes().getNamedItem(
                        "max").
                        getTextContent());
                }

            }

            assert (xMin != Double.MIN_VALUE && yMin != Double.MIN_VALUE &&
                xMax != Double.MIN_VALUE && yMax != Double.MIN_VALUE);

            if (units.equalsIgnoreCase("DD"))
            {
                xMin = AngleUnit.DD.convert(xMin, AngleUnit.RADIANS);
                xMax =
                    AngleUnit.DD.convert(xMax, AngleUnit.RADIANS);
                yMin =
                    AngleUnit.DD.convert(yMin, AngleUnit.RADIANS);
                yMax =
                    AngleUnit.DD.convert(yMax, AngleUnit.RADIANS);
            }
            else
            {
                if (units.equalsIgnoreCase("DMS"))
                {
                    xMin = AngleUnit.DMS.convert(xMin, AngleUnit.RADIANS);
                    xMax =
                        AngleUnit.DMS.convert(xMax, AngleUnit.RADIANS);
                    yMin =
                        AngleUnit.DMS.convert(yMin, AngleUnit.RADIANS);
                    yMax =
                        AngleUnit.DMS.convert(yMax, AngleUnit.RADIANS);
                }

            }

            sp = new Cartesian2DSpace(xMin, xMax, yMin, yMax);
        }
        else
        {
            throw new IllegalArgumentException("Unhandled space referenced: " +
                spaceClass);
        }

        logger.debug("Read in space: " + sp);
        return sp;
    }

    /**
     * Parses the following Views:
     * <ul>
     * <li> SimpleView </li>
     * </ul>
     * returning a {@link ViewFrame} corresponding to the view specified in
     * {@code viewNode}.
     *
     * @params viewNode the xml node containing the view to parse
     * @returns the {@link ViewFrame} corresponding to the parsed
     * {@code viewNode}
     */
    private ViewFrame parseView(Node viewNode)
    {
        assert (viewNode.getNodeName().equalsIgnoreCase("view"));

        ViewFrame viewFrame = null;

        String viewClass = viewNode.getAttributes().getNamedItem("class").
            getTextContent();
        NodeList children = viewNode.getChildNodes();
        if (viewClass.equalsIgnoreCase("SimpleView"))
        {
            String viewName = null;
            for (int c = 0; c <
                children.getLength(); c++)
            {
                Node child = children.item(c);
                if (child.getNodeName().equalsIgnoreCase("name"))
                {
                    assert (viewName == null);
                    viewName =
                        child.getTextContent();
                }
                else
                {
                    if (!child.getNodeName().startsWith("#"))
                    {
                        throw new IllegalArgumentException("Unhandled SimpleView child: " +
                            child.getNodeName());
                    }

                }
            }
            viewFrame = new SimpleView(viewName);
        }
        else
        {
            throw new IllegalArgumentException("Unhandled view referenced: " +
                viewClass);
        }

        logger.debug("Read in view: " + viewFrame);
        return viewFrame;
    }

    /**
     * Returns the XML {@code Document} corresponding to the given {@code File}.
     * Validates that the
     * {@code headNodeName.equalsIgnoreCase(actualHeadNodeName)}.
     *
     * @return an {@code Document} corresponding to the XML file given
     * @param xmlInputStream the XML file to parse
     * @param headNodeName the expected name of the root node of the file
     */
    public static Document getDocument(
        InputStream xmlInputStream,
        String headNodeName)
    {
        DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = null;
        Document theParsedDocument = null;

        try
        {
            docBuilder = fact.newDocumentBuilder();
            theParsedDocument =
                docBuilder.parse(xmlInputStream);
        }
        catch (ParserConfigurationException ex)
        {
            throw new IllegalArgumentException(ex);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException(ex);
        }
        catch (SAXException ex)
        {
            throw new IllegalArgumentException(ex);
        }

        String headName = theParsedDocument.getDocumentElement().getNodeName();
        if (!headName.equals(headNodeName))
        {
            throw new IllegalArgumentException("Expected node: " +
                headNodeName + " Found[" + headName + "]");
        }

        return theParsedDocument;
    }

    /**
     * Writes the XML scenario file to the specified file name.
     * @param filename name of the XML scenario file.
     */
    public void writeScenario(String filename)
    {
        try
        {
            DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = factory.newDocumentBuilder();

            Document document = docBuilder.newDocument();

            //  Add elements to the document, starting with the root
            Element root = document.createElement("Scenario");
            document.appendChild(root);

            //  Add the elements to the document
            this.addElements(document);

            //  From xerces (thank you!)
            XMLSerializer serializer = new XMLSerializer();
            serializer.setOutputCharStream(new java.io.FileWriter(filename));
            serializer.setOutputFormat(new OutputFormat(document, null, true));
            serializer.serialize(document);
        }
        catch (Exception ex)
        {
        }

    }

    /**
     * Adds elements to the document.
     * @param document the document to which to add the elements.
     */
    protected void addElements(Document document)
    {
        NodeList roots = document.getElementsByTagName("Scenario");

        if (roots.getLength() != 1)
        {
            logger.error("Output scenario is malformed");
        }

//        Node root = roots.item(0);

//   Add any other elements here.
    }

    /**
     * Writes the SIMDIS initialization data for each agent that implements ISIMDIS
     *
     * @param args_handler the arguments handler
     */
    public void simdisInitialize(ArgsHandler args_handler)
    {
        if (args_handler.getASIFileName() != null)
        {
            this.asi_file = args_handler.getFile(args_handler.getASIFileName());
        }

        if (this.asi_file != null)
        {
            try
            {
                FileWriter fw = new FileWriter(this.asi_file, false);
                PrintWriter pw = new PrintWriter(fw);

                pw.println("Version\t17");
                if (this.refLLA != null)
                {
                    pw.println("RefLLA\t" + refLLA.getX() + "\t" +
                        refLLA.getY() + "\t" + refLLA.getZ());
                }

                pw.println("CoordSystem\t\"" + getEarthModel().
                    getCoordinateSystem() + "\"");

                if (this.gog_file != null)
                {
                    pw.println("GOGFile\t\"" + this.gog_file + "\"");
                }

                if (this.ded_map != null)
                {
                    pw.println("DEDMap\t\"" + this.ded_map + "\"");
                }

                if (this.wvs_map != null)
                {
                    pw.println("WVSMap\t\"" + this.wvs_map + "\"");
                }

                if (this.classification_label != null)
                {
                    pw.println("Classification\t\"" + this.classification_label +
                        "\"\t" + this.classification_color);
                }

                pw.println("\n# Platform Initialization\n");
                pw.close();

                //  Don't do anything separate for jammers, since jammers are
                //  included in the aircraft set.
                for (Aircraft ac : aircraft)
                {
                    if (ac instanceof ISIMDIS)
                    {
                        ((ISIMDIS) ac).simdisInitialize(asi_file);
                    }

                }

                for (Platform t : targets)
                {
                    if (t instanceof ISIMDIS)
                    {
                        ((ISIMDIS) t).simdisInitialize(asi_file);

                        if (t instanceof SAMTEL)
                        {
                            for (Platform p : t.getSubordinates())
                            {
                                if (p instanceof Weapon)
                                {
                                    ((ISIMDIS) p).simdisInitialize(asi_file);
                                }

                            }
                        }
                    }
                }

                for (Radar r : radars)
                {
                    if (r instanceof ISIMDIS)
                    {
                        ((ISIMDIS) r).simdisInitialize(asi_file);
                    }

                }

                for (Jammer j : jammers)
                {
                    if (j instanceof ISIMDIS)
                    {
                        ((ISIMDIS) j).simdisInitialize(asi_file);
                    }

                }

                //  Receivers
                for (Receiver r : this.receivers)
                {
                    if (r instanceof ISIMDIS)
                    {
                        ((ISIMDIS) r).simdisInitialize(asi_file);
                    }

                }

                fw = new FileWriter(this.asi_file, true);
                pw =
                    new PrintWriter(fw);
                pw.println(
                    "\n# Platform Data\n#\tKeyword\tPlatformID\tTime\tLat\tLon\tAlt\tYaw\tPitch\tRoll");
                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }
}
