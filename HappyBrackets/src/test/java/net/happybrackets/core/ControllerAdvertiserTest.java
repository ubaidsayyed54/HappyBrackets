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

package net.happybrackets.core;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import net.happybrackets.core.BroadcastManager;

import net.happybrackets.controller.network.ControllerAdvertiser;
import net.happybrackets.controller.config.ControllerConfig;

public class ControllerAdvertiserTest {
	protected ControllerConfig 			env;
	protected ControllerAdvertiser 	advertiser;
	protected BroadcastManager			broadcastManager;

	@Before
	public void setUp() throws Exception {
		env 				= new ControllerConfig();
		env 				= env.load("src/test/config/test-controller-config.json", env);
		advertiser 			= new ControllerAdvertiser(broadcastManager);
		advertiser.start();
	}

	@After
	public void tearDown() throws Exception {
		advertiser.interrupt();
	}

	@Test
	public void test() {
		assert( advertiser.isAlive() );
	}

}
