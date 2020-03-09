package raft.chainvayler.compiler;

import java.lang.instrument.Instrumentation;

//import java.io.File;
//import java.lang.instrument.Instrumentation;
//import java.lang.management.ManagementFactory;
//import java.net.JarURLConnection;
//import java.net.URL;

/** runtime instrumentation entry point. see java.lang.instrument */
public class Agent {
	
    /** command line agent 
     * @throws Exception */
    public static void premain(String rootClassName, Instrumentation inst) throws Exception {
    	inst.addTransformer(new Transformer(rootClassName));
    }
   
    /** runtime attach agent 
     * @throws Exception */
    public static void agentmain(String rootClassName, Instrumentation inst) throws Exception {
    	inst.addTransformer(new Transformer(rootClassName));
    }
   
    /** attach to current VM and load agent (requires SUN tools VirtualMachine) */
//    public static void loadAgent() {
//    	try {
//            String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
//            int index = nameOfRunningVM.indexOf('@');
//            String pid = nameOfRunningVM.substring(0, index);
//   
//            String resource = "/" + Agent.class.getName().replace('.', '/') + ".class";
//            URL jarUrl = Agent.class.getResource(resource);
//            System.out.println(resource + " -> " + jarUrl);
//           
//            if ((jarUrl == null) || !jarUrl.toString().startsWith("jar:file:"))
//                  throw new IllegalStateException("couldnt determine the jar of Agent class");
//            
//            JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
//            File file = new File(connection.getJarFileURL().toURI());
//            System.out.println(file);
//           
////          URL url = connection.getJarFileURL();
////          System.out.println(url.getFile());
//           
//            VirtualMachine vm = VirtualMachine.attach(pid);
//            vm.loadAgent(file.getCanonicalPath());
////          vm.loadAgent(url.getFile());
//            vm.detach();
//           
//            System.out.println("--loaded agent--");
//		} catch (Exception e) {
//			throw new RuntimeException(e);
//		}
//    } 
}