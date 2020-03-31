/**
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Copyright (C) 2019 https://www.dmsystem.com.ve and contributors (see README.md file).
 */

package net.frontuari.component;

import net.frontuari.base.FTUProcessFactory;
import net.frontuari.process.BussinesPartnerConsignment;
import net.frontuari.process.CreateBusinessLocator;
import net.frontuari.process.ProductConsignment;
import net.frontuari.process.SalesReport;

/**
 * Process Factory
 */
public class ProcessFactory extends FTUProcessFactory {

	/**
	 * For initialize class. Register the process to build
	 * 
	 * <pre>
	 * protected void initialize() {
	 * 	registerProcess(PPrintPluginInfo.class);
	 * }
	 * </pre>
	 */
	@Override
	protected void initialize() {
		registerProcess(CreateBusinessLocator.class);
		registerProcess(BussinesPartnerConsignment.class);
		registerProcess(ProductConsignment.class);
		registerProcess(SalesReport.class);
	}

}
