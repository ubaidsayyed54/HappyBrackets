/*
 * Copyright 2016 Ollie Bown
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

package net.happybrackets.device.sensors;

import net.beadsproject.beads.data.DataBead;

/**
 * Gets notified when a sensor has been updated. No data is passed in this method. The implementor should independently query the sensor for data.
 *
 * Created by ollie on 1/06/2016.
 */
public interface SensorUpdateListener {

    void sensorUpdated();

}
