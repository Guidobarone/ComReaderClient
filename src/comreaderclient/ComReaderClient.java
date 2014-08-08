package comreaderclient;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * This version of the ComReaderClient example makes use of the 
 * SerialPortEventListener to avoid polling.
 *
 */
public class ComReaderClient
{
	private static final String CONFIGURATION_FILE = "Totem.properties";
	private static String totemId = "";
	private static String portName = "";
	private static String databaseIp = "";
	private static String databaseName = "";
	private static String databaseUser = "";
	private static String databasePassword = "";
    
    void connect (String portName) throws Exception
    {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        if ( portIdentifier.isCurrentlyOwned() )
        {
            System.out.println("Error: Port is currently in use");
        }
        else
        {
            CommPort commPort = portIdentifier.open(this.getClass().getName(),2000);
            
            if ( commPort instanceof SerialPort )
            {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(9600,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
                
                InputStream in = serialPort.getInputStream();
                OutputStream out = serialPort.getOutputStream();
                
                (new Thread(new SerialWriter(out))).start();
                
                serialPort.addEventListener(new SerialReader(in));
                serialPort.notifyOnDataAvailable(true);

            }
            else
            {
                System.out.println("Error: Only serial ports are handled.");
            }
        }     
    }
    
    /**
     * Handles the input coming from the serial port.
     * A new line character is treated as the end of a block.
     */
    public static class SerialReader implements SerialPortEventListener 
    {
        private InputStream in;
        private byte[] buffer = new byte[1024];
        
        public SerialReader ( InputStream in )
        {
            this.in = in;
        }
        
        public void serialEvent(SerialPortEvent arg0) {
            int data;
          
            try
            {
                int len = 0;
                while ( ( data = in.read()) > -1 )
                {
                    if ( data == '\n' ) {
                        break;
                    }
                    buffer[len++] = (byte) data;
                }
                String result = new String(buffer,0,len).trim();
                System.out.println(result);
                String[] token = result.split("@");
                boolean b = insertReading(totemId, token[0], token[1]);
                if(b) {
                    //int i=0x1B;
                    int i=27;
                    char c = (char)i;
                    //char c = '';
                    String output = token[0]+"@"+c+"[3q"+"\r";
                    SerialWriter.out.write(output.getBytes("ASCII"));
                    System.out.println("output="+output);
                }
            }
            catch ( IOException e )
            {
                e.printStackTrace();
                System.exit(-1);
            }             
        }

    }

    /** */
    public static class SerialWriter implements Runnable 
    {
        static OutputStream out;
        
        public SerialWriter ( OutputStream out )
        {
        	SerialWriter.out = out;
        }
        
        public void run ()
        {
            try
            {                
                int c = 0;
                while ( ( c = System.in.read()) > -1 )
                {
                	SerialWriter.out.write(c);
                }                
            }
            catch ( IOException e )
            {
                e.printStackTrace();
                System.exit(-1);
            }            
        }
    }
    
    private static boolean insertReading(String id_totem, String reader_id , String value) {

            System.out.println("insertReading "+id_totem+" "+reader_id+" "+value);

            Connection conn = null;
            CallableStatement cstmt = null;

            try {

                    conn = getConnection();

                String sProc = "call P_Service_Reading_Insert(?,?,?)";
                cstmt = conn.prepareCall(sProc);
                cstmt.setString(1, id_totem);
                cstmt.setString(2, reader_id);
                cstmt.setString(3, value);
                    int result = cstmt.executeUpdate();
                System.out.println("P_Service_Reading_Insert executed: " + result);

                    if (result>0){
                            return true;
                    }

            } catch (Exception e) {
                    e.printStackTrace();
            } finally {

                    try {
                            conn.close();
                            cstmt.close();
                    } catch (SQLException e) {
                            e.printStackTrace();
                    }

            }
            return false;
    }

    private static Connection getConnection() {

            try {
                Connection conn = DriverManager.getConnection("jdbc:mysql://"+databaseIp+
                                                                          "/"+databaseName+
                                                                     "?user="+databaseUser+
                                                                 "&password="+databasePassword);
                return conn;

            } catch (Exception e) {
                    e.printStackTrace();
            }
            return null;

    }
    
    public static void main (String[] args)
    {
		// read properties file
		Properties properties = new Properties();
		try {
		    properties.load(new FileInputStream(CONFIGURATION_FILE));
		} catch (IOException e) {
			System.err.println("Warning: error loading configuration file " + CONFIGURATION_FILE);
		}
        totemId = properties.getProperty("TOTEMID");
        portName = properties.getProperty("PORTNAME");
        databaseIp = properties.getProperty("DATABASEIP");
        databaseName = properties.getProperty("DATABASENAME");
        databaseUser = properties.getProperty("DATABASEUSER");
        databasePassword = properties.getProperty("DATABASEPASSWORD");
        
        try
        {
            (new ComReaderClient()).connect(portName);
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
    }
}