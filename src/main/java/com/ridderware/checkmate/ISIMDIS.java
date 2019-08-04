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

import java.io.File;

/**
 * Interface to be implemented by agents that are to be visualized in SIMDIS.
 *
 * @author Jeff Ridder
 */
public interface ISIMDIS
{
    /**
     * Method to write initialization data to a SIMDIS .asi file for the 
     * implementing agent.  See SIMDIS User Manual Appendix A.1 for details.
     * @param asi_file the SIMDIS .asi file to write to.
     */
    public void simdisInitialize(File asi_file);

    /**
     * Method to write time data to a SIMDIS .asi file for the implementing agent.  
     * See SIMDIS User Manual Appendix A.2 for details.
     * @param time time of the update.
     * @param asi_file SIMDIS .asi file to write to.
     */
    public void simdisUpdate(double time, File asi_file);
}
