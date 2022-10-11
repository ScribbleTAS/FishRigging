package de.scribble.lp.fishrigging;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtils;

public class FishManip {
	
	private File fileLocation;

	public FishManip(File saveFile) {
		fileLocation=saveFile;
		createFile();
	}
	
	public File getFileLocation() {
		return fileLocation;
	}

	/**
	 * Creates a file on startup
	 * @return The default data
	 */
	private List<String> createFile() {
		if (fileLocation.exists())
			return null;
		
		List<String> toWrite= new ArrayList<>();
		toWrite.add("#This file was generated by FishManip, the author is Scribble. Leave blank to disable this feature. Everything starting with a hashtag is a comment\n"
				+ "#\n"
				+ "#If fishmanip is active, fishing rng will be the best possible rng"
				+ "#Once an item has been caught, this file will update and remove the topmost item from your list\n"
				+ "#If there is an error, the file will show you which line and what the error is\n"
				+ "#\n"
				+ "#Some items require damage values and or enchantments. The syntax for that is as follows:\n"
				+ "#\n"
				+ "#<item_name>;damage:<damage_value>;enchant:<first_enchantment_name>[<ench_level>],<second_enchantment_name>[<ench_level>]\n"
				+ "#\n"
				+ "#Example: fishing_rod_treasure;damage:0;enchant:unbreaking[1],lure[3]\n"
				+ "#\n"
				+ "#\n"
				+ "#-----------------------------Possible items-----------------------------\n"
				+ "#\n"
				+ "#\n");
		
		for(String item : PossibleItems.getNames()) {
			toWrite.add("#"+item);
		}
		try {
			FileUtils.writeLines(fileLocation, toWrite);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return toWrite;
	}
	
	/**
	 * Checks if the file has any uncommented lines and therefore if the fishmanip is active
	 * <p>
	 * Doesn't check for mistakes
	 * @return True if there is at least one line uncommented
	 */
	public boolean isActive() {
		List<String> completeFile;
		try {
			completeFile = readFile();	// Read the file
		} catch (IOException e) {
			System.out.println("Failed to read file for FishManip for some reason");
			e.printStackTrace();
			return false;
		}
		for (String line : completeFile) {	// Iterate through all the lines
			if (line.startsWith("#")) {	// Ignore comments
				continue;
			} else if(line!=null&&!line.isEmpty()) {	//If a line is not null or empty
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Reads {@link #fileLocation} and gets the topmost ItemStack.
	 * <p>
	 * Afterwards it saves the file with the line removed or with error lines in the file.
	 * @return The specified itemstack or a barrier item if an error occured
	 */
	public ItemStack getItemFromTop() {
		List<String> completeFile;	// The file to be read
		List<String> output = new ArrayList<>();	// The lines for the output file after the file has been parsed.
		
		ItemStack barrier = new ItemStack(Blocks.BARRIER);	// The barrier item that will be returned in case the parsing fails
		barrier.setStackDisplayName("Something went wrong in FishRigging. Check your file");	// Add a display name
		
		ItemStack topmostItem = barrier;	// topmostItem will be the item that will ultimately be returned... Right now it is filled with the barrier but will change when parsing was successful
		
		try {
			completeFile = readFile();	// Read the file
		} catch (IOException e) {
			System.out.println("Failed to read file for FishManip for some reason");
			e.printStackTrace();
			return topmostItem;
		}
		
		int linenumber = 0;	// Line number. Important for checking what is the topmost item.
		for(String line : completeFile) {
			
			if(line.startsWith("#") || line.isEmpty()) {  //Comments!
				output.add(line);	// Add comments to the output
				continue;
			}
			
			if(line.contains("Mistake in this line:")) { //If there is already an error in this line, remove the old error message
				line=line.replaceFirst("Mistake in this line:.*->\\s*", "");
			}
			
			linenumber++;	// Increment linenumber on valid entries in the file
			
			QueuedItem item = null;	// This will be the successfully parsed object from which an actual item stack will be constructed
			try {
				item = parseLine(line);
			} catch (Exception e) {
				output.add(errorLine(line, e.getMessage())); // If lineparsing fails, write an error message to the output
				continue;
			}
			
			try {
				if(linenumber == 1) {	// Do this only on the topmost item
					topmostItem = PossibleItems.constructItem(item);	// Construct an itemStack from the QueuedItem
					continue;	// Make sure to skip adding this to the output, since we want the topmostItem to be removed if parsing was successful
				}
				PossibleItems.constructItem(item);	// This will check for every other line after the first one if the parsed item is valid, to to error checking
				output.add(line);	// Add the rest of the lines to the output
			} catch (Exception e) {
				output.add(errorLine(line, e.getMessage()));	// If constructing fails, add an error message to the current line
			}
		}
		if(fileLocation.exists()) {	// Delete existing file before writing to avoid overwriting issues
			fileLocation.delete();
		}
		try {
			FileUtils.writeLines(fileLocation, output);	// Write the output to the same location
		} catch (IOException e) {
			e.printStackTrace();
		}
		return topmostItem;
	}
	
	/**
	 * Constructs an error message from the current line
	 * @param line The line to concatenate to the error message
	 * @param message The error message, why did this parsing fail?
	 * @return The line with added error message
	 */
	private String errorLine(String line, String message) {
		
		return String.format("Mistake in this line: %s -> %s", message, line);
	}
	
	/**
	 * Parses the line into a Queued Item which holds the data for item construction
	 * @param line The line to parse
	 * @return The QueuedItem
	 * @throws Exception If something went wrong and if it should be printed to the file
	 */
	private QueuedItem parseLine(String line) throws Exception{
		
		line = line.replaceFirst("\\s*#.*", ""); // Get rid of comments right of the actual data
		
		String[] blocks = line.split(";");	// Split name;damage;enchantments into it's blocks
		
		String name;	// Values for constructing the QueuedItem at return
		Integer damage = null;	// Damage and enchs can be null if it was not parsed
		Map<Enchantment, Integer> enchs = null;
		
		name = blocks[0];	// Name is mandatory
		
		for(int i = 1; i<blocks.length; i++) {	// For loop for checking a different order
			
			Pattern damagepattern = Pattern.compile("damage:(\\d+)");	// Regex magic for reading damage property
			Matcher damagematcher = damagepattern.matcher(blocks[i]);
			
			if (damagematcher.find()) {	// If a damage property was found
				damage = Integer.parseInt(damagematcher.group(1));
			} else {	// Else check for enchantments... Maybe more properties in the future
				if (blocks[i].startsWith("enchant:")) {
					enchs = new HashMap<>();	// Initialize enchmap because at this point we know there is an enchantment proprty
					Pattern enchpattern = Pattern.compile("(\\w+)\\[(\\d)\\]");	// More regex magic to read enchantment name and level

					String enchString = blocks[i].replace("enchant:", "");	// Remove the enchant: before the values to not interfere with the regex
					String[] enchSplit = enchString.split(",");	// Split multiple enchantments and iterate over them

					for (String ench : enchSplit) {
						Matcher enchmatcher = enchpattern.matcher(ench);
						if (enchmatcher.find()) {	// If it finds an enchantment
							Enchantment key = Enchantment.getEnchantmentByLocation(enchmatcher.group(1));	// Group 1 is th e enchantment name
							if(key==null) {
								throw new Exception(String.format("Can't read enchantment: %s", enchmatcher.group(1)));
							}
							int lvl = Integer.parseInt(enchmatcher.group(2));	// Group 2 is the level
							enchs.put(key, lvl);	// Put the enchantment and the level into the hashmap
						}
					}
				}
			}
		}
		
		return new QueuedItem(name, damage, enchs);	// Construct the QueuedItem from parsed items
	}

	/**
	 * Declutter method to either create a new file or read the existing file
	 * @return
	 * @throws IOException
	 */
	private List<String> readFile() throws IOException{
		if(fileLocation.exists())
			return FileUtils.readLines(fileLocation, StandardCharsets.UTF_8);
		else
			return createFile();
	}
	
	/**
	 * Holds all the possible items for fishing, as well as damage values and whether enchantments are possible.
	 * <p>
	 * Also holds comments for file creation
	 * @author Scribble
	 *
	 */
	enum PossibleItems {
		// Add more items to this enum to check for them! Check the constructor for how to specify parameters
		COD("cod", new ItemStack(Items.FISH, 1, 0), null),
		SALMON("salmon", new ItemStack(Items.FISH, 1, 1), null),
		CLOWNFISH("clownfish", new ItemStack(Items.FISH, 1, 2), null),
		PUFFERFISH("pufferfish", new ItemStack(Items.FISH, 1, 3), null),
		LEATHER_BOOTS("leather_boots", new ItemStack(Items.LEATHER_BOOTS), Pair.of(0, 58), false, "Possible damage: 0-58, This item can't be enchanted, Example: leather_boots;damage:0"),
		WATER_POTION("water_potion", waterPotion(), null),
		FISHING_ROD_JUNK("fishing_rod_junk", new ItemStack(Items.FISHING_ROD), Pair.of(0, 57), false, "Possible damage: 0-57, This item can't be enchanted, Example: fishing_rod_junk;damage:0"),
		INC_SAC("ink_sac", new ItemStack(Items.DYE, 10, 0), null),
		TRIPWIRE_HOOK("tripwire_hook", new ItemStack(Blocks.TRIPWIRE_HOOK), null),
		ROTTEN_FLESH("rotten_flesh", new ItemStack(Items.ROTTEN_FLESH), null),
		WATERLILY("waterlily", new ItemStack(Blocks.WATERLILY), null),
		NAME_TAG("name_tag", new ItemStack(Items.NAME_TAG), null),
		SADDLE("saddle", new ItemStack(Items.SADDLE), null),
		BOW("bow", new ItemStack(Items.BOW), Pair.of(0, 96), true, "Possible damage: 0-96, This item has to be enchanted, Example: bow;damage:0;enchant:infinity[1],unbreaking[3]"),
		FISHING_ROD_TREASURE("fishing_rod_treasure", new ItemStack(Items.FISHING_ROD), Pair.of(0, 57), true, "Possible damage: 0-57, This item has to be enchanted, Example: fishing_rod_treasure;damage:0;enchant:lure[1],unbreaking[3]"),
		BOOK("book", new ItemStack(Items.ENCHANTED_BOOK), null, true, "This item has to be enchanted, Example: book;enchant:frost_walker[1]")
		; //<-- At the end of an enum list has to be a semicolon. Putting it here so you can see it...
		
		/**
		 * The name that should be recognized by the parser.<br>
		 * This is only because of the two fishing rods, one being junk and one being treasure
		 */
		private final String name;
		/**
		 * The itemstack as a base for constructing.<br>
		 * This is useful since fish items still have different damage values and inc sac always having the quantity 10
		 */
		private final ItemStack stack;
		/**
		 * The damage range of "damagable" items. getLeft holds minimum damage, getRight holds maximum
		 */
		private final Pair<Integer, Integer> damageRange;
		/**
		 * Indicates if enchantments are mandatory or forbidden. The current system doesn't allow for being optional tho
		 */
		private final boolean enchantmentMandatory;
		/**
		 * The comment behind the value when creating the file
		 */
		private final String comment;
		
		/**
		 * Constructor for non damaged items that can't hold an enchantment or damage value
		 * @param name {@link #name}
		 * @param stack {@link #stack}
		 * @param comment {@link #comment}
		 */
		PossibleItems(String name, ItemStack stack, String comment) {
			this(name, stack, null, false, comment);
		}
		
		/**
		 * Constructor for items that can hold enchantments or damage values
		 * @param name {@link #name}
		 * @param stack {@link #stack}
		 * @param damageRange {@link #damageRange} (Can be null to disable damage checking)
		 * @param enchMandatory {@link #enchantmentMandatory}
		 * @param comment {@link #comment}
		 */
		PossibleItems(String name, ItemStack stack, @Nullable Pair<Integer, Integer> damageRange, boolean enchMandatory, String comment) {
			this.name = name;
			this.stack = stack;
			this.damageRange = damageRange;
			this.enchantmentMandatory = enchMandatory;
			this.comment = comment;
		}
		
		/**
		 * Creates a water potion item.
		 * Yes this is how to do it...
		 * @return A water potion
		 */
		private static ItemStack waterPotion() {
			ItemStack stack = new ItemStack(Items.POTIONITEM);
			return PotionUtils.addPotionToItemStack(stack, PotionTypes.WATER);
		}
		
		/**
		 * Names and comments for each enum value
		 * @return leather_boots	#Possible damage: 0-58, This item can't be enchanted, Example: leather_boots;damage:0	
		 */
		public static List<String> getNames(){
			List<String> out = new ArrayList<>();
			PossibleItems[] values = values();
			for(PossibleItems items : values) {
				out.add(items.toString());
			}
			return out;
		}
		
		/**
		 * Gets the possible item from a name
		 * @param name {@link #name}
		 * @return The according PossibleItems value
		 */
		public static PossibleItems getFromName(String name) {
			PossibleItems[] values = values();
			for(PossibleItems items : values) {
				if(items.name.equals(name)) {
					return items;
				}
			}
			return null;
		}
		
		/**
		 * Get's a stack from the specified name
		 * @param name {@link #name}
		 * @return
		 */
		public static ItemStack getStack(String name) {
			return getFromName(name).stack.copy();
		}
		
		/**
		 * Constructs and item from the specified QueuedItem object. Also checks for errors during construction
		 * @param item The item to check and to convert to an ItemStack
		 * @return The successfully created itemstack
		 * @throws Exception Exception if item construction failed. Used for printing in the file
		 */
		public static ItemStack constructItem(QueuedItem item) throws Exception{
			
			Exception e = isValid(item);
			if(e!=null)
				throw e;
			
			ItemStack itemStack = getStack(item.name); // Set item damage after validation
			if(item.damage!=null) {
				itemStack.setItemDamage(item.damage);
			}
			if(item.enchantments!=null) {	// Set enchantments after validation
				EnchantmentHelper.setEnchantments(item.enchantments, itemStack);
			}
			return itemStack;
		}
		
		/**
		 * Checks if the {@link QueuedItem} is valid
		 * @param item The item to check
		 * @return Null if the item is valid, or an Exception with a message if it's invalid
		 */
		private static Exception isValid(QueuedItem item) {
			if(item == null) {
				return new Exception("Syntax error, can't read this line");
			}
			return isValid(item.name, item.damage, item.enchantments);
		}
		
		/**
		 * Checks if separate values are valid
		 * @param name {@link #name}
		 * @param damage Damage to check for the {@link #damageRange}. Null if no damage property was provided
		 * @param enchlist Enchantment list to check for. Null if no enchantment property was provided
		 * @return Null if the item is valid, or an Exception with a message if it's invalid
		 */
		private static Exception isValid(String name, @Nullable Integer damage, @Nullable Map<Enchantment, Integer> enchlist) {
			
			PossibleItems item = getFromName(name);
			
			if(item == null)
				return new Exception("Couldn't find this in the list of possible fished items");
			
			Exception e = isValidDamage(item, damage);
			if(e != null)
				return e;
			
			e = isValidEnchantments(item, enchlist);
			if (e != null)
				return e;
			
			return null;
		}
		
		/**
		 * Checks if the damage range checks out
		 * @param item Item to check
		 * @param damage Damage to check for the {@link #damageRange}. Null if no damage property was provided
		 * @return Null if the damage is valid, or an Exception with a message if it's invalid
		 */
		private static Exception isValidDamage(PossibleItems item, @Nullable Integer damage) {
			Pair<Integer, Integer> range = item.damageRange;
			if(damage!=null) { // If a damage property was provided
				if(range == null) {
					return new Exception("This item doesn't accept a damage property");
				}
				if(damage>=range.getLeft() && damage<=range.getRight()) {
					return null;
				} else {
					return new Exception(String.format("The damage is not within %s-%s", range.getLeft(), range.getRight()));
				}
			} else { // If a no damage property was provided
				if(range != null) {
					return new Exception(String.format("The damage property is mandatory for this item. It has to be within %s-%s", range.getLeft(), range.getRight()));
				}
				return null;
			}
		}
		
		/**
		 * Checks if enchantments are valid on the specified item
		 * @param item Item to check
		 * @param enchlistMap Enchantment list to check for. Null if no enchantment property was provided
		 * @return Null if the enchantment is valid, or an Exception with a message if it's invalid
		 */
		private static Exception isValidEnchantments(PossibleItems item, @Nullable Map<Enchantment, Integer> enchlistMap) {
			
			if(enchlistMap == null) { // If there was no enchlist provided
				if(item.enchantmentMandatory) {
					return new Exception("The enchantment property is mandatory for this item");
				}else {
					return null;
				}
			}
			
			if(!item.enchantmentMandatory) // Test if enchantment is mandatory
				return new Exception("This item doesn't accept an enchantment property");
			
			Set<Enchantment> enchlist = enchlistMap.keySet();
			
			for(Enchantment firstEnch : enchlist) {
				
				ItemStack stack = item.stack.copy();
				
				if(!firstEnch.canApply(stack) && !stack.getItem().equals(Items.ENCHANTED_BOOK)) { // Test if enchantments can apply
					return new Exception(String.format("This item can't be enchanted with %s", firstEnch.getName()));
				}
				
				for(Enchantment secondEnch : enchlist) { // Test for compatibility
					if(firstEnch.equals(secondEnch))
						continue;
					if(!firstEnch.isCompatibleWith(secondEnch)) 
						return new Exception(String.format("Enchantment %s is incompatible with %s", firstEnch.getName(), secondEnch.getName()));
				}
			}
			
			Set<Entry<Enchantment, Integer>> enchSet = enchlistMap.entrySet(); // Testing for max min enchantment
			
			for(Entry<Enchantment, Integer> enchEntry : enchSet) {
				Enchantment ench = enchEntry.getKey();
				int lvl = enchEntry.getValue();
				if(ench.getMaxLevel() < lvl) {
					return new Exception(String.format("The level of %s is too high. Max: %s", ench.getName(), ench.getMaxLevel()));
				}
				if(ench.getMinLevel() > lvl) {
					return new Exception(String.format("The level of %s is too low. Min: %s", ench.getName(), ench.getMinLevel()));
				}
			}
			return null;
		}
		
		/**
		 * Adds a comment to the name of the possible item
		 */
		@Override
		public String toString() {
			String out = name;
			if(comment!=null) {
				out = out.concat("\t\t\t\t\t\t\t\t#"+comment);
			}
			return out;
		}
	}
	
	/**
	 * Data class for holding name damage and enchantments
	 * @author Scribble
	 *
	 */
	class QueuedItem{
		
		String name;
		Integer damage;
		Map<Enchantment, Integer> enchantments;
		
		public QueuedItem(String name) {
			this(name, null, null);
		}
		
		public QueuedItem(String name, @Nullable Integer damage, @Nullable Map<Enchantment, Integer> enchantments) {
			this.name = name;
			this.damage = damage;
			this.enchantments = enchantments;
		}
		
	}
}
