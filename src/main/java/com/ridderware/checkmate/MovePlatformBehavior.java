/*
 * 
 * Coadaptive Heterogeneous simulation Engine for Combat Kill-webs and 
 * Multi-Agent Training Environment (CHECKMATE)
 *
 * Copyright 2007 Jeff Ridder
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

import com.ridderware.fuse.Behavior;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The interface for platform kinematics.  This employs the Strategy pattern, where
 * kinematic behaviors are encapsulated separately from their MobilePlatform clients.
 * @author Jeff Ridder
 */
public abstract class MovePlatformBehavior extends Behavior implements IXML
{
    private MobilePlatform platform;

    private boolean moving;

    private double kinematic_update_period;

    private double time_of_last_kinematic_update;

    /**
     * Creates a new instance of MovePlatformBehavior
     * @param platform the MobilePlatform to be moved by this behavior.
     */
    public MovePlatformBehavior(MobilePlatform platform)
    {
        this.platform = platform;
        this.moving = true;
        this.kinematic_update_period = 1.;
        this.time_of_last_kinematic_update = 0.;
    }

    /**
     * Returns the platform controlled by this behavior.
     * @return platform.
     */
    public MobilePlatform getPlatform()
    {
        return this.platform;
    }

    /**
     * Sets a flag to indicate whether the platform is moving.
     * @param moving moving flag.
     */
    public void setMoving(boolean moving)
    {
        this.moving = moving;
    }

    /**
     * Returns a flag indicating whether the platform is moving.
     * @return true if moving, false otherwise.
     */
    public boolean isMoving()
    {
        return this.moving;
    }

    /**
     * Resets the kinematic parameters for this behavior.
     */
    public void initialize()
    {
        this.time_of_last_kinematic_update = 0.;
    }

    /**
     * Returns the number of seconds between kinematic updates.
     * @return update period.
     */
    public double getKinematicUpdatePeriod()
    {
        return kinematic_update_period;
    }

    /**
     * Sets the number of seconds between kinematic updates.
     * @param kinematic_update_period update period.
     */
    public void setKinematicUpdatePeriod(double kinematic_update_period)
    {
        this.kinematic_update_period = kinematic_update_period;
    }

    /**
     * Returns the time of the last kinematic update.
     * @return time.
     */
    public double getTimeOfLastKinematicUpdate()
    {
        return time_of_last_kinematic_update;
    }

    /**
     * Sets the time of last kinematic update.
     * @param time_of_last_kinematic_update time.
     */
    public void setTimeOfLastKinematicUpdate(double time_of_last_kinematic_update)
    {
        this.time_of_last_kinematic_update = time_of_last_kinematic_update;
    }

    /**
     * Abstract method called by MovePlatformBehavior to update the position of
     * the owning platform.
     * @param time current time.
     */
    public abstract void movePlatform(double time);

    /**
     * Called by the FUSE framework.  Returns the next time to perform this behavior.
     * @param current_time current time.
     * @return next time to perform the behavior.
     */
    @Override
    public double getNextScheduledTime(double current_time)
    {
        double next_time = current_time;

        if (platform.getStatus() == Platform.Status.ACTIVE && this.moving)
        {
            next_time += getKinematicUpdatePeriod();
        }
        return next_time;
    }

    /**
     * Called by the FUSE framework to perform the behavior.  This method calls
     * movePlatform.
     * @param current_time the current time.
     */
    @Override
    public void perform(double current_time)
    {
        movePlatform(current_time);
    }

    /**
     * Called by the owning platform class to activate the behavior and platform.
     * @param time current time.
     */
    public abstract void activate(double time);

    /**
     * Sets the behavior's attributes by parsing the XML.
     * @param node root node for the behavior.
     */
    @Override
    public void fromXML(Node node)
    {
        NodeList children = node.getChildNodes();

        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);

            if (child.getNodeName().equalsIgnoreCase("kinematic-update-period"))
            {
                this.setKinematicUpdatePeriod(Double.parseDouble(child.getTextContent()));
            }
        }
    }

    /**
     * Creates XML nodes containing the behavior's attributes.
     *
     * @param node root node for the behavior.
     */
    @Override
    public void toXML(Node node)
    {
        Document document = node.getOwnerDocument();

        Element e = document.createElement("kinematic-update-period");
        e.setTextContent(String.valueOf(this.kinematic_update_period));
        node.appendChild(e);
    }
}
