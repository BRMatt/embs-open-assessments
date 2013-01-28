package lsi.edi;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.StringToken;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class VerificationAnalysis extends Verification implements ActionListener {

	protected Hashtable<String, Vector<Double>> latencies, deliveries;
	protected JFrame frame;
	protected JTabbedPane tabbedPane;
	protected JTextArea textarea;
	protected JPanel commands;
	protected boolean monitor=false;

	
	public VerificationAnalysis(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException  {

		super(container, name);
		frame=new JFrame("Basic Communication Latency Analysis");
		tabbedPane = new JTabbedPane();

		
		JButton but = new JButton("Generate report");
		but.addActionListener(this);
		but.setActionCommand("generate");
		JButton res = new JButton("Reset stats");
		res.addActionListener(this);
		res.setActionCommand("reset");
		JButton mon = new JButton("Toggle message monitoring");
		mon.addActionListener(this);
		mon.setActionCommand("monitor");		
		
		commands = new JPanel();
		commands.setLayout(new GridLayout(0,1));
		commands.add(but);
		commands.add(res);
		commands.add(mon);
		
		textarea=new JTextArea();
		JScrollPane scrollPane = new JScrollPane(textarea); 
		textarea.setEditable(false);

		
		
		tabbedPane.addTab("status",scrollPane);
		tabbedPane.addTab("commands",commands);
		
		
		frame.getContentPane().add(tabbedPane);
		frame.setSize(400,300);
		
		frame.setVisible(true);
		
		reset();

	}
	
	
	public void fire() throws IllegalActionException{
		
		for(int i=0;i<input.getWidth();i++){
			if(input.hasToken(i)){
				
				RecordToken packet = (RecordToken)input.get(i);

				int id =  ((IntToken)packet.get("id")).intValue();
				double release = ((DoubleToken)packet.get("releasetime")).doubleValue();
				double commfinish = getDirector().getCurrentTime();
				double period = ((DoubleToken)packet.get("period")).doubleValue();
				
				double totalresponsetime = commfinish - release;
				String s="task "+id+" ---";
				
				if(totalresponsetime<=period){s=s+"DEADLINE MET ";} else {s=s+"DEADLINE MISSED ";}
				s = s+ "resp time: "+totalresponsetime+" --- period: "+period;
				StringToken st = new StringToken(s);
				output.send(0,st);
				notifyReceipt(id, "task "+id, commfinish, totalresponsetime);				
			}
			
		}
		
	}

	
	
	public void actionPerformed(ActionEvent arg0) {

		if(arg0.getActionCommand()=="reset"){reset();}
		else if(arg0.getActionCommand()=="generate"){textarea.setText(report()[0]);}
		else if(arg0.getActionCommand()=="monitor"){monitor=!monitor;}
		
		
	}
	

	
	public void notifyReceipt(int id, String name, double latency) {
		this.notifyReceipt(name, latency);

	}

	public void notifyReceipt(int id, String name, double time, double latency) {
		this.notifyReceipt(name, time, latency);

	}

	public void notifyReceipt(String name, double time, double latency) {

		boolean contains=latencies.containsKey(name);
		if(!contains){ // first entry of a given message
			latencies.put(name, new Vector<Double>());
			deliveries.put(name, new Vector<Double>());
		}
	    // adds new latency value to the list of the respective message
		latencies.get(name).add(new Double(latency));
		deliveries.get(name).add(new Double(time));
			
		
		if(monitor)textarea.setText(textarea.getText()+System.getProperty("line.separator")+name+","+time+","+latency);
	}


	public void notifyReceipt(String name, double latency) {

		boolean contains=latencies.containsKey(name);
		if(!contains){ // first entry of a given message
			latencies.put(name, new Vector<Double>());
			deliveries.put(name, new Vector<Double>());
		}
		else{          // adds new latency value to the list of the respective message
			latencies.get(name).add(new Double(latency));
			deliveries.get(name).add(new Double(Double.NaN));
			
		}


	}



	public String[] report() {
		return report(",");
	}



	public String[] report(String sep) {


			StringBuffer report = new StringBuffer();

			for (Enumeration<String> k=latencies.keys(); k.hasMoreElements();) 
			{

				String name = k.nextElement();
				Vector<Double> v = latencies.get(name);


				double maxi=0; //Double.MIN_VALUE;
				double mini=0; //Double.MAX_VALUE;
				double sum=0.0;
				int n=0; 

				double[] numbers = new double[v.size()];
				
				for (Enumeration<Double> values = v.elements(); values.hasMoreElements();)
				{
					double cur = values.nextElement().doubleValue();
					numbers[n]=cur;
					if (maxi==0) {maxi = cur;}
					else if (cur>maxi)maxi=cur;
					if (mini==0) {mini = cur;}
					else if (cur<mini)mini=cur;
					sum = sum + cur;
					n=n+1;

				}
				
				Arrays.sort(numbers);
				
				if(n>0){
					
					int q1= n/4;
					int q2= n/2;
					int q3 =3*n/4;
					
					Double lowQ = new Double(numbers[q1]);
					Double median = new Double(numbers[q2]);
					Double uppQ = new Double(numbers[q3]);
					
					Double max = new Double(maxi);
					Double min = new Double(mini);
					Double avg = new Double(sum/n); 

					String eol = System.getProperty("line.separator"); 
//					report.append(name+sep+max.toString()+sep+min.toString()+sep+avg.toString()+eol);
					report.append(name+sep+avg.toString()+sep+min.toString()+sep+lowQ.toString()+sep+median.toString()+sep+uppQ.toString()+sep+max.toString()+eol);
					
				}
			}

			String[] fullreport = {report.toString()};

			System.out.println(report.toString());

			return fullreport;

	}




	public void reset() {
		latencies = new Hashtable();
		deliveries = new Hashtable();
		textarea.setText("");


	}
	
}
