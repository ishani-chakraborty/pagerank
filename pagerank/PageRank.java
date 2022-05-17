package pagerank;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.zip.GZIPInputStream;

/**
 *
 * Compute PageRank(tm) on a set of URLs
 */
public class PageRank {
	private int MaxIterations = 1000; // guarantees to stop, even if it fails to converge
	private Map<String,Integer> urlMap; // maps url-> page index
	private Map<String,Integer> inLinksCounts; // maps url -> # of inlinks
	private List<String> urls; // the reverse of the urlMap, as an ArrayList
	private List<List<Integer>> links; // the adjacency list for each page, indexed by page index

	public PageRank() {
		urlMap = new HashMap<String, Integer>();
		inLinksCounts = new HashMap<String, Integer>();
		urls = new ArrayList<String>();
		links = new ArrayList<List<Integer>>();
	}
	
	/**
	 * Computes PageRank on the loaded set of urls and their associated links.
	 * Uses mapping from url -> integer id to index into the vector of pageranks.
	 * @param lambda the parameter for the random jump
	 * @param tau the threshold for convergence testing
	 * @return the array of pageranks, indexed by url id
	 */
	public double[] calculatePageRank(double lambda, double tau) {
		double[] I = new double[urls.size()];
		double[] R = new double[urls.size()];
		double init = 1.0/urls.size();
		for (int i = 0; i < I.length; i++)
			I[i]  = init;
         
		// if it converges before MaxIterations, we return at that point
		for (int iter = 0; iter < MaxIterations; iter++) {
			// lines 10-12.
			for (int i = 0; i < R.length; i++)
				R[i]  = lambda/urls.size(); // random jump component
			// For all pages:
			double weightToAddToEveryone = 0.0;
			for (int pageId = 0; pageId < urls.size(); pageId++) {
				List<Integer> outLinks = links.get(pageId);
				double weight = (1-lambda) * I[pageId];
				if(outLinks.isEmpty()) {
					weightToAddToEveryone += weight / ((double) urls.size()); 
				} else {
					// update all targets q in |Q|
					for (Integer q : outLinks) {
						// ((1-lambda)*Ip) / |P|
						R[q] += weight / ((double) outLinks.size());
					}
				}
			}
			
			for (int p = 0; p < R.length; p++)
				R[p] += weightToAddToEveryone;
			// Check for convergence. use either L2 norm or L1 norm
			@SuppressWarnings("checkpoint")
			double l1 = calculateL2(I, R);
			double l2 = calculateL1(I, R);
			double norm = l2;
			if(norm <= tau) {
				return R;
			}
			for (int i = 0; i < R.length; i++)
				I[i] = R[i];
		}
		return R;
	}

	/**
	 * @param a the first vector
	 * @param b the second vector
	 * @return the L1 norm of the vector difference, |a - b|
	 */
	private double calculateL1(double[] a, double[]b) {
		double norm = 0;
		for (int i = 0; i < a.length; i++)
			norm += Math.abs(a[i] - b[i]);
		return norm;
	}

	/**
	 * @param a the first vector
	 * @param b the second vector
	 * @return the L2 norm of the vector difference, ||a - b||
	 */
	private double calculateL2(double[] a, double[]b) {
		double norm = 0;
		for (int i = 0; i < a.length; i++)
			norm += Math.pow(a[i] - b[i], 2);
		return Math.sqrt(norm);
	}

	/**
	 * Reads in a file of source, target url pairs, one per line, delimited by the
	 * tab character ('\t').
	 * Maps each url string to an integer id, constructs an array of the urls for output
	 * access. Constructs an array of adjacency lists, indexed by url id, containing the
	 * ids of the targets for each source. 
	 * @param inFile the file to load
	 */
	private void load(String inFile) {
		try {
			BufferedReader br = 
					new BufferedReader(new InputStreamReader(
							new GZIPInputStream(new FileInputStream(inFile)), "UTF-8"));
			String s;
			while((s = br.readLine()) != null) {
				String []tokens = s.split("\t"); // split on tab character
				if (tokens.length != 2) continue; // skip any line that doesn't have two
				String source = tokens[0];
				String target = tokens[1];
				int sId, tId;
				if (urlMap.containsKey(source)) {
					sId = urlMap.get(source);
				} else {
					// brand new url
					// number it and put it in the map
					sId = urls.size();
					urlMap.put(source, sId);
					urls.add(source);
					// create an empty adjacency list
					links.add(new ArrayList<Integer>());
				}
				if (urlMap.containsKey(target)) {
					tId = urlMap.get(target);
				} else {
					//brand new url
					// number it and put it in the map
					tId = urls.size();
					urlMap.put(target, tId);
					urls.add(target);
					// create an empty adjacency list
					links.add(new ArrayList<Integer>());
				}
				// update the adjacency list
				links.get(sId).add(tId);
				// increment the inlink counter
				inLinksCounts.put(target, inLinksCounts.getOrDefault(target, 0) + 1);
			}
			br.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Prints out the top K url pagerank pairs to the specified file
	 * @param scores the pagerank scores to print out
	 * @param K the number of items to print
	 * @param outputFile the file to print them to
	 */
	private void printTopK(double[] scores, int K, String outputFile) {
		K = Math.min(urls.size(), K);
		PriorityQueue<Map.Entry<String, Double>> scored = new PriorityQueue<>(Map.Entry.<String, Double>comparingByValue());
		for (int i = 0; i < urls.size(); i++) {
			Map.Entry<String, Double> doc = new AbstractMap.SimpleEntry<String, Double>(urls.get(i), scores[i]);
			scored.add(doc);
			if (scored.size() > K) {
				scored.poll();
			}
		}
		// reverse the queue
		ArrayList<Map.Entry<String, Double>> top =
				new ArrayList<Map.Entry<String, Double>>();
		top.addAll(scored);
		top.sort(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()));
		int rank = 1;
		try {
			PrintWriter writer = new PrintWriter(new FileWriter(outputFile, false));
			// Output documents with the highest pagerank:
			for (Map.Entry<String, Double> sdoc : top) {
				writer.format("%s\t%d\t%f\n", sdoc.getKey(), rank++, sdoc.getValue());
			}
			writer.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
	
	/**
	 * Prints out the top K url inlink count pairs to the specified file
	 * @param K the number of items to print
	 * @param outputFile the file to print them to
	 */
	private void printTopKLinks(int K, String outputFile) {
		K = Math.min(inLinksCounts.size(), K);
		PriorityQueue<Map.Entry<String, Integer>> scored = new PriorityQueue<>(Map.Entry.<String, Integer>comparingByValue());
		for (Map.Entry<String, Integer> doc : inLinksCounts.entrySet()) {
			scored.add(doc);
			if (scored.size() > K) {
				scored.poll();
			}
		}
		// reverse the queue
		ArrayList<Map.Entry<String, Integer>> top =
				new ArrayList<Map.Entry<String, Integer>>();
		top.addAll(scored);
		top.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()));
		try {
			PrintWriter writer = new PrintWriter(new FileWriter(outputFile, false));
			// Output documents with the highest inlink counts:
			int rank = 1;
			for (Map.Entry<String, Integer> sdoc : top) {
				writer.println(sdoc.getKey() + "\t" + rank + "\t" + sdoc.getValue());
			}
			writer.close();
		} catch  (IOException ex) {
			ex.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		// Read arguments from command line; or use sane defaults for IDE.
		String inputFile = args.length >= 1 ? args[0] : "data/links.srt.gz";
		double lambda = args.length >=2 ? Double.parseDouble(args[1]) : 0.15;
		double tau = args.length >=3 ? Double.parseDouble(args[2]) : 0.0001;
		int k = args.length >=4 ? Integer.parseInt(args[3]) : 75;

		PageRank pagerank = new PageRank();
		pagerank.load(inputFile);
		double[] scores = pagerank.calculatePageRank(lambda, tau);
		
		pagerank.printTopK(scores, k, "pagerank.txt");
		pagerank.printTopKLinks(k, "inlinks.txt");
	}
}
