package gui.configuration;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

public class ClassLoadHelper {
	
	public static ArrayList<Class<?>> findClassesContainingName(Class<?> objectClass) {
    	ArrayList<Class<?>> classes = new ArrayList<Class<?>>();
    	
        String classpath = System.getProperty("java.class.path");
        String[] paths = classpath.split(System.getProperty("path.separator"));

        for (String path : paths) {
            File file = new File(path);
            if (file.exists() && file.isDirectory()) {
            	findClasses(file, objectClass,classes);
            }
        }
        
        return classes;
    }
	
	public static boolean isInstanceOf(Class<?> cls, Class<?> objectClass) {
		
		Class<?> superClass = cls.getSuperclass();
		
		if(superClass != null){			
			if(superClass.equals(Object.class)){
				return false;
			}else if(superClass.equals(objectClass)){
				return true;
			}else{
				return isInstanceOf(superClass, objectClass);
			}
		}
		
		return false;
	}
	
	public static void findClasses(File file, Class<?> objectClass, ArrayList<Class<?>> cls) {
		try {
			for (File f : file.listFiles()) {
	    		if(f.isDirectory()){
	    			findClasses(f, objectClass,cls);
	    		}else{
	    			if(f.getName().endsWith(".class")){
	    				String path = "";
	    				
	    				if(System.getProperty("os.name").contains("Windows"))
	    					path = f.getAbsolutePath().replace("\\", "/").split("/bin/")[1].replaceAll(".class", "").replaceAll("/", ".");
	    				else
	    					path = f.getAbsolutePath().split("/target/")[1].replaceAll(".class", "").replaceAll("/", ".");
	    				
		    			Class<?> cl = Class.forName(path);
		    			
		    			boolean isAbstract = Modifier.isAbstract(cl.getModifiers());

		    			if(!isAbstract && isInstanceOf(cl, objectClass)){
		    				cls.add(cl);
		    			}
		    			
	    			}
	    		}
	      	}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

}
