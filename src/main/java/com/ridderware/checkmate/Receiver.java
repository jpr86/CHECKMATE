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

import com.ridderware.checkmate.Platform.Status;
import java.util.HashSet;
import com.ridderware.fuse.Behavior;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Abstract base class for all receivers.
 * @author Jeff Ridder
 */
public abstract class Receiver extends CMSystem implements ISIMDIS
{
    private Platform parent;

    private double scan_period;

    // angle of arrival accuracy in radians.
    private double aoa_accuracy;

    private boolean simdis_display;

    private HashSet<Radar> tracks = new HashSet<>();

    private ScanReceiverBehavior scan_receiver_behavior;

    /**
     * Creates a new instance of Receiver
     * @param name name of the receiver.
     * @param world CMWorld in which the receiver plays.
     */
    public Receiver(String name, CMWorld world)
    {
        super(name, world);

        this.createReceiver();
    }

    /**
     * Sets the initial state of the receiver upon creation.
     */
    protected final void createReceiver()
    {
        this.aoa_accuracy = 0.;
        this.scan_period = 1.;
        this.simdis_display = false;

        this.scan_receiver_behavior = new ScanReceiverBehavior();
        this.addBehavior(this.scan_receiver_behavior);
    }

    /**
     * Activates the receiver.
     * @param time current time.
     */
    public void activate(double time)
    {
        this.scan_receiver_behavior.setEnabled(true);
        this.simdisUpdate(time, getWorld().getASIFile());
    }

    /**
     * Sets the parent platform.
     * @param parent a Platform object.
     */
    public void setParent(Platform parent)
    {
        this.parent = parent;
    }

    /**
     * Returns the parent platform.
     * @return a Platform object.
     */
    public Platform getParent()
    {
        return this.parent;
    }

    /**
     * Flags whether to draw receiver detection rings in SIMDIS.
     * @param simdis_display true = draw in SIMDIS, false = don't draw (default).
     */
    public void setSIMDISDisplay(boolean simdis_display)
    {
        this.simdis_display = simdis_display;
    }

    /**
     * Returns SIMDIS display flag
     * @return true if SIMDIS will draw detection rings, false (default) otherwise.
     */
    public boolean getSIMDISDisplay()
    {
        return this.simdis_display;
    }

    /**
     * Sets the angle-of-arrival accuracy of the receiver.
     * @param aoa_accuracy angle-of-arrival accuracy in radians.
     */
    public void setAoAAccuracy(double aoa_accuracy)
    {
        this.aoa_accuracy = aoa_accuracy;
    }

    /**
     * Returns the angle-of-arrival accuracy of the receiver.
     * @return the angle-of-arrival accuracy in radians.
     */
    public double getAoAAccuracy()
    {
        return this.aoa_accuracy;
    }

    /**
     * Sets the initial attributes of the receiver before each simulation run.
     */
    public void initialize()
    {
        tracks.clear();
    }

    /**
     * Returns a collection of tracks.
     * @return collection of emitter tracks.
     */
    protected HashSet<Radar> getTracks()
    {
        return tracks;
    }

    /**
     * Returns the scan period of the receiver.
     * @return scan period.
     */
    public double getScanPeriod()
    {
        return scan_period;
    }

    /**
     * Sets the scan period of the receiver.
     * @param scan_period scan period in seconds.
     */
    public void setScanPeriod(double scan_period)
    {
        this.scan_period = scan_period;
    }

    /**
     * Returns the Scan behavior for the receiver.
     * @return scan receiver behavior.
     */
    public ScanReceiverBehavior getScanReceiverBehavior()
    {
        return this.scan_receiver_behavior;
    }

    /**
     * Calculates and returns the angle of arrival of the target signal
     * relative to the heading of the parent platform of this receiver.
     * The AoA returned has Gaussian noise applied to the true bearing consistent
     * with the AoA accuracy for this receiver.
     * @param tgt a Radar object.
     * @return the angle of arrival in radians relative to the parent platform heading.
     */
    public double calculateAoA(Radar tgt)
    {
        //  True angle without heading
        double angle = CMWorld.getEarthModel().azimuthAngle(getParent().
            getLocation(), tgt.getParent().getLocation());

        //  Modify relative to nose heading of the parent
        double heading = 0.;

        if (getParent() instanceof MobilePlatform)
        {
            heading = ((MobilePlatform) getParent()).getHeading();
        }

        double aoa = angle - heading;

        //  Apply Gaussian noise
        aoa += getRNG().nextGaussian() * this.aoa_accuracy;

        //  Normalize between -Pi and +Pi
        aoa = AngleUnit.normalizeAngle(aoa, -Math.PI);

        return aoa;
    }

    /**
     * Abstract method calld by ScanReceiverBehavior to sense the environment and make tracks.
     * @param current_time current time.
     */
    public abstract void scanReceiver(double current_time);
    //  We probably need a behavior here that tells the antenna to scan, then
    //  takes the results and tells the jammers to look for RA's.
    /**
     * Behavior to periodically scan the environment.
     */
    protected class ScanReceiverBehavior extends Behavior
    {
        /**
         * Called by the Swarm framework to determine the next time to perform the behavior.
         * @param current_time current time.
         * @return next time at which to perform the behavior.
         */
        @Override
        public double getNextScheduledTime(double current_time)
        {
            double scan_time = current_time;
            if (parent.getStatus() == Status.ACTIVE)
            {
                //  Increment by a delta-t
                //  Pseudo-randomize the scan period by +/- 10% -- this is to prevent
                //  "synching" with the radar scan.
                double delta_t = (0.9 + 0.2 * getRNG().nextDouble()) *
                    getScanPeriod();
                scan_time += delta_t;
            }

            return scan_time;
        }

        /**
         * Called by the Swarm framework to perform the behavior.
         * @param current_time current time.
         */
        @Override
        public void perform(double current_time)
        {
            scanReceiver(current_time);

            if (parent instanceof IReceiverContainer && !tracks.isEmpty())
            {
                ((IReceiverContainer) parent).reportTracks((Receiver) getAgent(),
                    tracks);
            }
        }
    }

    /**
     * Sets the system's attributes by parsing the XML.
     * @param node root node for the system.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();

        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);
            if (child.getNodeName().equalsIgnoreCase("scan-period"))
            {
                this.setScanPeriod(Double.parseDouble(child.getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("aoa-accuracy"))
            {
                this.setAoAAccuracy(Math.toRadians(Double.parseDouble(child.getTextContent())));
            }
            else if (child.getNodeName().equalsIgnoreCase("simdis-display"))
            {
                if (child.getAttributes().getNamedItem("value").getTextContent().
                    equalsIgnoreCase("true"))
                {
                    this.setSIMDISDisplay(true);
                }
                else
                {
                    this.setSIMDISDisplay(false);
                }
            }
        }
    }

    /**
     * Creates XML nodes containing the system's attributes.
     *
     * @param node root node for the system.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e;

        //  Create scan-period element
        e = document.createElement("scan-period");
        e.setTextContent(String.valueOf(this.scan_period));
        node.appendChild(e);

        //  Create aoa-accuracy element
        e = document.createElement("aoa-accuracy");
        e.setTextContent(String.valueOf(Math.toDegrees(this.aoa_accuracy)));
        node.appendChild(e);

        //  SIMDIS display flag
        e = document.createElement("simdis-display");
        e.setAttribute("value", String.valueOf(this.simdis_display));
        node.appendChild(e);
    }
}
