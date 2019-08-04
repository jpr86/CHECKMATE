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

import org.w3c.dom.Node;

/**
 * Interface declaring methods for setting an objects attributes by parsing them
 * from XML, and for creating XML nodes containing those attributes.
 * @author Jeff Ridder
 */
public interface IXML
{
    /**
     * Declaration of a method to set an object's attributes by parsing the input XML.
     * @param node root XML node for the object.
     */
    public void fromXML(Node node);

    /**
     * Declaration of a method to create XML nodes containing an object's attributes.
     * @param node root XML node for the object.
     */
    public void toXML(Node node);
}
