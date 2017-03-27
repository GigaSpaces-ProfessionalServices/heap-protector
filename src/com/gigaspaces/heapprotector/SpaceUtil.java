package com.gigaspaces.heapprotector;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openspaces.core.GigaSpace;

import com.gigaspaces.metadata.SpaceTypeDescriptor;
import com.j_spaces.core.admin.IRemoteJSpaceAdmin;
import com.j_spaces.core.admin.SpaceRuntimeInfo;

/**
 * Space related common functions
 */
public class SpaceUtil {

	private static final Logger logger = Logger.getLogger(SpaceUtil.class.getName());

	public static Map<String, Integer> getSpaceClassesInstanceCount(GigaSpace space) throws Exception {
		HashMap<String, Integer> classInstanceCount = new HashMap<String, Integer>();
		IRemoteJSpaceAdmin spaceAdmin = (IRemoteJSpaceAdmin) space.getSpace().getAdmin();
		SpaceRuntimeInfo rtInfo = spaceAdmin.getRuntimeInfo();

		for (int i = 0; (i < rtInfo.m_ClassNames.size()); i++) {
			classInstanceCount.put(rtInfo.m_ClassNames.get(i), rtInfo.m_NumOFEntries.get(i));
		}

		return classInstanceCount;
	}

	/**
	 * Returns a count of the objects in the provided space.
	 *
	 * @param space
	 *            Space proxy
	 * @param objectName
	 *            Class name to count instances
	 * @return Count of the instances
	 * @throws java.rmi.RemoteException
	 *             Errors connecting to space
	 */
	public static int getEntryCount(GigaSpace space, String objectName) {

		IRemoteJSpaceAdmin spaceAdmin;
		try {
			spaceAdmin = (IRemoteJSpaceAdmin) space.getSpace().getAdmin();
			SpaceRuntimeInfo rtInfo = spaceAdmin.getRuntimeInfo();

			logger.info("Object types found in Space = " + rtInfo.m_ClassNames.size());

			for (int i = 0; (i < rtInfo.m_ClassNames.size()); i++) {

				if (logger.isLoggable(Level.FINE))
					logger.fine(rtInfo.m_ClassNames.get(i));

				if (rtInfo.m_ClassNames.get(i).equals(objectName)) {
					if (logger.isLoggable(Level.FINEST))
						logger.finest(rtInfo.m_NumOFEntries.get(i).toString());
					return rtInfo.m_NumOFEntries.get(i);
				}
			}

		} catch (RemoteException e) {
			logger.severe(e.getMessage());
			e.printStackTrace();
		}
		return 0;
	}

	static HashMap<String, Boolean> rootClasses = new HashMap<>();
	public static boolean isBaseClass(GigaSpace space, String className) throws Exception {
		
		if (rootClasses.containsKey(className)) 
			return rootClasses.get(className);
		
		IRemoteJSpaceAdmin spaceAdmin = (IRemoteJSpaceAdmin) space.getSpace().getAdmin();
		SpaceRuntimeInfo rtInfo = spaceAdmin.getRuntimeInfo();

		Iterator<String> iter = rtInfo.m_ClassNames.iterator();
		while (iter.hasNext()) {
			String _className = iter.next();
			// need to iterate all classes and see if its one of their super
			// types
			SpaceTypeDescriptor desc = space.getTypeManager().getTypeDescriptor(_className);
			String superTypeName = desc.getSuperTypeName();
			if (superTypeName.indexOf(className) > -1)
			{
				rootClasses.put(className,true);
				return true;
			}
		}
		rootClasses.put(className,false);
		return false;
	}
}
