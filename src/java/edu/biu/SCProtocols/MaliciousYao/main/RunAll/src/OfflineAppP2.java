import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

import edu.biu.protocols.yao.offlineOnline.primitives.BucketList;
import edu.biu.protocols.yao.offlineOnline.primitives.ExecutionParameters;
import edu.biu.protocols.yao.offlineOnline.primitives.LimitedBundle;
import edu.biu.protocols.yao.offlineOnline.specs.OfflineProtocolP2;
import edu.biu.protocols.yao.primitives.CheatingRecoveryCircuitCreator;
import edu.biu.protocols.yao.primitives.CircuitInput;
import edu.biu.protocols.yao.primitives.CommunicationConfig;
import edu.biu.protocols.yao.primitives.CryptoPrimitives;
import edu.biu.protocols.yao.primitives.KProbeResistantMatrix;
import edu.biu.scapi.circuits.circuit.BooleanCircuit;
import edu.biu.scapi.circuits.fastGarbledCircuit.FastGarbledBooleanCircuit;
import edu.biu.scapi.circuits.fastGarbledCircuit.ScNativeGarbledBooleanCircuit;
import edu.biu.scapi.comm.Party;
import edu.biu.scapi.exceptions.CircuitFileFormatException;
import edu.biu.scapi.exceptions.NoSuchPartyException;
import edu.biu.scapi.interactiveMidProtocols.ot.otBatch.otExtension.OTExtensionMaliciousReceiver;

public class OfflineAppP2 {	
	private static final int PARTY = 2;
	private static final String HOME_DIR = "C:/GitHub/Development/MaliciousYaoProtocol/MaliciousYao";
	private static final String COMM_CONFIG_FILENAME = HOME_DIR + "/assets/conf/Parties1.properties";

	private OTExtensionMaliciousReceiver otReceiver;
	private CommunicationConfig commConfig;
	private CryptoPrimitives primitives;
	
	private String circuitFile;
	private String crCircuitFile;
	private String mainBucketsPrefix;
	private String crBucketsPrefix;
	private String mainMatrix;
	private String crMatrix;
	
	BooleanCircuit mainCircuit;
	BooleanCircuit crCircuit;
	
	public OfflineAppP2(String circuitFile, String circuitInputFile, String crCircuitFile, String mainBucketsPrefix, 
			String crBucketsPrefix, String mainMatrix, String crMatrix){
		
		this.circuitFile = circuitFile;
		this.crCircuitFile = crCircuitFile;
		this.mainBucketsPrefix = mainBucketsPrefix;
		this.crBucketsPrefix = crBucketsPrefix;
		this.mainMatrix = mainMatrix;
		this.crMatrix = crMatrix;
		
		try {
			 commConfig = new CommunicationConfig(COMM_CONFIG_FILENAME);
		} catch (IOException e) {
			System.exit(1);
		}
		
		primitives = CryptoPrimitives.defaultPrimitives(8);
		commConfig.connectToOtherParty(1 + primitives.getNumOfThreads());
		
		CircuitInput input = null;
		// we read the circuit and this party's input from file
		try {
			mainCircuit = new BooleanCircuit(new File(circuitFile));
			input = CircuitInput.fromFile(circuitInputFile, mainCircuit, PARTY);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CircuitFileFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		crCircuit = (new CheatingRecoveryCircuitCreator(crCircuitFile, input.size())).create();
		try {
			otReceiver = initMaliciousOtReceiver(mainCircuit.getNumberOfInputs(2), commConfig);
		} catch (NoSuchPartyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void run(int N1, int B1, int s1, double p1, int N2, int B2, int s2, double p2, String outputFile) throws IOException {
			
		FastGarbledBooleanCircuit[] mainGbc;
		FastGarbledBooleanCircuit[] crGbc;
		
		int numOfThread = primitives.getNumOfThreads();
		if (numOfThread >0){
			mainGbc = new ScNativeGarbledBooleanCircuit[numOfThread];
			crGbc = new ScNativeGarbledBooleanCircuit[numOfThread];
		}else{
			mainGbc = new ScNativeGarbledBooleanCircuit[1];
			crGbc = new ScNativeGarbledBooleanCircuit[1];
			
		}
		for (int i=0; i<mainGbc.length; i++){
			mainGbc[i] = new ScNativeGarbledBooleanCircuit(circuitFile, true, false, true);
		}
		
		for (int i=0; i<crGbc.length; i++){
			crGbc[i] = new ScNativeGarbledBooleanCircuit(crCircuitFile, true, false, true);
		}
		
		ExecutionParameters mainExecution = new ExecutionParameters(mainCircuit, mainGbc, N1, s1, B1, p1);
		ExecutionParameters crExecution = new ExecutionParameters(crCircuit, crGbc, N2, s2, B2, p2);
		
		OfflineProtocolP2 protocol = null;
		
		FileWriter output = new FileWriter(outputFile, true);
		output.append("parameters = " + N1 + "_" + B1 + "_" + s1 + "_" + p1 + "_" + N2 + "_" + B2 + "_" + s2 + "_" + p2+ "\n");
		output.append("Threads number\n");
		
		int numExecutions = 1;
		int numThreads = 0;
		long[] times = new long[numExecutions];
		for (int j=0; j<1; j++){
			output.append(numThreads + " threads,");
			primitives = CryptoPrimitives.defaultPrimitives(numThreads);
			System.out.println("start execute "+ numExecutions +" times with "+ numThreads +" threads.");
			
			for (int i=0; i<numExecutions; i++){
				// we start counting the running time just before estalishing communication 
				long start = System.nanoTime();
				
				// and run the protocol
				protocol = new OfflineProtocolP2(mainExecution, crExecution, primitives, commConfig, otReceiver);
				
				
				System.out.println(String.format("Starting Offline protocol (P2)"));
				protocol.run();
				
				// we measure how much time did the protocol take
				long end = System.nanoTime();
				long runtime = (end - start) / 1000000;
				System.out.println("Offline protocol party 1 took " + runtime + " miliseconds.");
				output.append(runtime+",");
				times[i] = runtime;
			}
			output.append("\n");
			long evarage = 0;
			for (int i=0; i<numExecutions; i++){
				evarage += times[i];
			}
			System.out.println("average time to offline = "+(evarage/numExecutions)+" millis");
			numThreads+=4;
		}
		
		output.close();
		
		System.out.println(String.format("Saving buckets to files..."));
		long start = System.nanoTime();
		
		BucketList<LimitedBundle> mainBuckets = protocol.getMainBuckets();
		BucketList<LimitedBundle> crBuckets = protocol.getCheatingRecoveryBuckets();
		try {
			mainBuckets.saveToFiles(mainBucketsPrefix);
			crBuckets.saveToFiles(crBucketsPrefix);
			KProbeResistantMatrix.saveToFile(protocol.getMainProbeResistantMatrix(), mainMatrix);
			KProbeResistantMatrix.saveToFile(protocol.getCheatingRecoveryProbeResistantMatrix(), crMatrix);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		long end = System.nanoTime();
		long runtime = (end - start) / 1000000;
		System.out.println("Saving buckets took " + runtime + " miliseconds.");
		
		commConfig.close();
	}
	
	/**
	 * Initializes the malicious OT receiver.
	 * @param numOts The number of OTs to run.
	 */
	private OTExtensionMaliciousReceiver initMaliciousOtReceiver(int numOts, CommunicationConfig communication) {
		//Get the ip and port of the receiver.
		Party maliciousOtServer = communication.maliciousOtServer();
		String serverAddress = maliciousOtServer.getIpAddress().getHostAddress();
		int serverPort = maliciousOtServer.getPort();
		//Create the malicious OT receiver using the ip, port and number of OTs.
		return new OTExtensionMaliciousReceiver(serverAddress, serverPort, numOts);
	}
}
