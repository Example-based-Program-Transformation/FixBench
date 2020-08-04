package nl.tudelft.ti2806.pl1.geneAnnotation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeSet;

/**
 * Storage location of all the genes of the TKK_REF with their information
 * included.
 * 
 * @author ChakShun
 * @since 2-6-2015
 */
public class ReferenceGeneStorage {

	/** Index for the start coordinate in the file. */
	private static final int INDEX_START = 3;
	/** Index for the end coordinate in the file. */
	private static final int INDEX_END = 4;
	/** Index for the score in the file. */
	private static final int INDEX_SCORE = 5;
	/** Index for the strand input in the file. */
	private static final int INDEX_STRAND = 6;
	/** Index for the attributes in the file. */
	private static final int INDEX_ATTRIBUTES = 8;
	/** Index for the index of the mutation in the file. */
	private static final int MUTATION_INDEX = 3;
	/** Index for the name of the mutation in the file. */
	private static final int MUTATION_NAME = 0;

	/** All the genes and information extracted from the information file. */
	private TreeSet<ReferenceGene> referenceGenes;

	/** All the known drug resistance mutations. */
	private Map<Integer, String> drugResMuts;

	/**
	 * Constructor reading the gff3 gene files, extracting all the possible
	 * genes in the reference genome.
	 * 
	 * @param genePath
	 *            Path to the file from which information should be extracted.
	 * @param mutationPath
	 *            Path to the file containing the drug resistance mutations.
	 */
	public ReferenceGeneStorage(final String genePath, final String mutationPath) {
		this.referenceGenes = extractReferenceGenes(genePath);
		if (mutationPath != null) {
			drugResMuts = extractMutations(mutationPath);
			connectGeneMutations();
		}
	}

	/**
	 * Extracts all the mutation information given file.
	 * 
	 * @param mutationPath
	 *            Path to the file containing the drug resistance mutations.
	 * @return Returns a hashmap containing the names of the mutations.
	 */
	private Map<Integer, String> extractMutations(final String mutationPath) {
		Map<Integer, String> temp = new HashMap<Integer, String>();
		Scanner sc = null;
		try {
			sc = new Scanner(new BufferedReader(new InputStreamReader(
					ReferenceGene.class.getClassLoader().getResourceAsStream(
							mutationPath), "UTF-8")));
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				if (!line.contains("##")) {
					String[] columns = line.split("\\t");
					String[] linesplit = columns[0].split(":");
					String[] info = linesplit[1].split(",");
					temp.put(Integer.parseInt(info[MUTATION_INDEX]),
							info[MUTATION_NAME]);
				}
			}
			sc.close();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return temp;
	}

	/**
	 * Extracts all the references genes from the decorationV5_20130412.gff
	 * file.
	 * 
	 * @param file
	 *            File from which gene information to be extracted.
	 * @return HashSet containing all the genes for the reference genome.
	 */
	private TreeSet<ReferenceGene> extractReferenceGenes(final String file) {
		TreeSet<ReferenceGene> ret = new TreeSet<ReferenceGene>(
				new RefGeneCompare());
		Scanner sc = new Scanner(ReferenceGene.class.getClassLoader()
				.getResourceAsStream(file));
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			if (line.contains("CDS")) {
				String[] info = line.split("\\t");
				String name = info[INDEX_ATTRIBUTES].split(";")[1].replace(
						"Name=", "");
				ret.add(new ReferenceGene(Integer.parseInt(info[INDEX_START]),
						Integer.parseInt(info[INDEX_END]), Double
								.parseDouble(info[INDEX_SCORE]),
						info[INDEX_STRAND], name));
			}
		}
		sc.close();
		return ret;
	}

	/**
	 * @return the name genes.
	 */
	public TreeSet<ReferenceGene> getReferenceGenes() {
		return referenceGenes;
	}

	/**
	 * Checks for the given position whether it falls into a intragenic region.
	 * 
	 * @param index
	 *            Index of the position to be checked.
	 * @return Whether the index is in a intragenic region.
	 */
	public boolean isIntragenic(final int index) {
		for (ReferenceGene rg : getReferenceGenes()) {
			if (rg.isIntragenic(index)) {
				return true;
			}
		}
		return false;
	}

	/** Connect the mutations with each referenceGene object. */
	private void connectGeneMutations() {
		for (ReferenceGene rg : this.getReferenceGenes()) {
			for (Integer key : this.getDrugResistanceMutations().keySet()) {
				if (rg.isIntragenic(key)) {
					rg.addMutation(key,
							this.getDrugResistanceMutations().get(key));
				}
			}
		}
	}

	/**
	 * Checks for the given position whether it is a mutation resulting in a
	 * change of gene.
	 * 
	 * @param index
	 *            Index of the position to be checked.
	 * @return Whether the index results in a change of gene.
	 */
	public boolean containsMutationIndex(final int index) {
		for (ReferenceGene rg : this.getReferenceGenes()) {
			if (rg.containsMutationIndex(index)) {
				return true;
			}
		}
		return false;
	}

	/** @return the map containing the know drug resistance mutations. */
	public Map<Integer, String> getDrugResistanceMutations() {
		return drugResMuts;
	}

	// @Override
	// public String toString() {
	// String res = "";
	// for (ReferenceGene rg : this.getReferenceGenes()) {
	// res += rg.toString() + "\n";
	// }
	// return res;
	// }

	// public static void main(final String[] args) {
	// ReferenceGeneStorage RGS = new ReferenceGeneStorage(
	// "decorationV5_20130412.gff", "resistanceCausingMutations.tsv");
	// }
}
