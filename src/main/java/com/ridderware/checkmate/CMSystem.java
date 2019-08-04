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

/**
 * Base class for all systems in the simulation.
 *
 * @author Jeff Ridder
 */
public abstract class CMSystem extends CMAgent
{
    /**
     * Creates a new instance of CMSystem
     * @param name name of the system.
     * @param world CMWorld in which it exists.
     */
    public CMSystem(String name, CMWorld world)
    {
        super(name, world);
    }
}
