import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;
/* Jackson Gray
 * COSC 4310
 * 11/20/2022
 */
public class SimpleCache{
    private static final int ADDRESS_SIZE = 32;
    private static final int ADDRESS_GENERATION_SIZE = 14;
    public static int blockAmount;
    public static void main(String[] args){
        //The main method reads the file addresses.txt to load the addresses,
        //then asks for block amount, associativity option, and replacement option.
        Scanner scan = new Scanner(System.in);

        //File generation (optional)        
        System.out.println("Would you like to generate a new set of addresses? Y/N");
        if(scan.nextLine().equalsIgnoreCase("Y")){
            System.out.println("How many addresses would you like to generate?");
            generateAddresses(scan.nextInt());
            System.out.println("Addresses generated.");
        }
        else{
            System.out.println("Using existing file.");
        }
        
        //File scanning
        List<String> addresses = new ArrayList<String>();
        System.out.println("Scanning file...");
        try{
            File file = new File("addresses.txt");
            Scanner fileScan = new Scanner(file);
            //Top 2 lines contains instructions, so they need to be skipped.
            fileScan.nextLine();
            fileScan.nextLine();
            while(fileScan.hasNextLine()){
                for(String s:fileScan.nextLine().split(" ")){
                    if(!s.equals("")){
                        addresses.add(correctAddress(s));
                    }
                }
            }
            System.out.printf("%d addresses found!\n", addresses.size());
        }
        catch(IOException e){
            e.printStackTrace();
        }

        //Other parameters:
        //Block Amount
        blockAmount = -1;
        String message = "How many sets? (This is the total amount of sets. Multi-way caches will distribute these sets.)";
        while(blockAmount < 0 || !is2N(blockAmount)){
            System.out.println(message);
            blockAmount = scan.nextInt();
            message = "Invalid number of sets. Set amount must be a value v = 2^n.";
        }

        //Set associativity
        int associativityOption = -1;
        message = "Choose an associativity. 0 = Direct Map, 1 = Set associative. 2 = Fully associative.";
        while(associativityOption < 0 || associativityOption > 2){
            System.out.println(message);
            associativityOption = scan.nextInt();
            message = "Invalid option. Please enter 0, 1, or 2.";
        }

        //Number of ways
        int wayCount = -1;
        message = "Please select 2 way or 4 way cache by entering 2 or 4.";
        while(associativityOption == 1 && !(wayCount == 2 || wayCount == 4)){
            System.out.println(message);
            wayCount = scan.nextInt();
            message = "Invalid option. Please enter 2 or 4.";
        }
        scan.close();
        
        new SimpleCache(associativityOption, wayCount, addresses);
    }

    SimpleCache(int associativityOption, int wayCount, List<String> addresses){
        //associativityOption: 0 = Direct Map, 1 = Set associative, 2 = Fully associative
        //wayCount: Number of ways for the cache (Set associative caches are limited to 2 or 4 way)
        //addresses is comprised of any amount of ADDRESS_SIZE bit addresses represented by Strings of length 32.
        if(associativityOption == 0){//Direct Map
            printCache(directMap(addresses));
        }
        else if(associativityOption == 1){//Set associative
            printCache(setMap(addresses, wayCount));
        }
        else{//Fully associative
            printCache(fullMap(addresses));
        }
    }

    public Block[] directMap(List<String> addresses){
        int blockSize = 32, hits = 0;
        Block[] cache = new Block[blockAmount];
        for(int i = 0; i < cache.length; i++){
            cache[i] = new Block();
        }

        int offsetAmount = (int)(Math.log(blockSize)/Math.log(2));
        int indexSize = (int)(Math.log(blockAmount)/Math.log(2));
        for(String address:addresses){
            Block temp = new Block(address);
            String offset = temp.getData().substring(ADDRESS_SIZE-offsetAmount);
            String index = temp.getData().substring(ADDRESS_SIZE-offsetAmount-indexSize,ADDRESS_SIZE-offsetAmount);
            String tag = temp.getData().substring(0,ADDRESS_SIZE-offsetAmount-indexSize);
            temp.setParams(tag, index, offset);
            int blockIndex = binaryToInt(index);
            if(cache[blockIndex].isValid() && cache[blockIndex].getTag().equals(tag)){
                //HIT
                hits++;
            }
            cache[blockIndex] = temp;
        }
        System.out.printf("Direct Map miss ratio: %d%s\n",(int)(100*((float)(addresses.size()-hits))/addresses.size()),"%");
        return cache;
    }

    public Block[][] setMap(List<String> addresses, int ways){
        int blockSize = 32, blocksPerWay = blockAmount/ways;
        Block[][] caches = new Block[ways][blocksPerWay];
        for(Block[] b:caches){
            for(int i = 0; i < b.length; i++){
                b[i] = new Block();
            }
        }

        int offsetAmount = (int)(Math.log(blockSize)/Math.log(2));
        int indexSize = (int)(Math.log(blocksPerWay)/Math.log(2));

        //Initializing LRU Queue
        LRUQueue<Integer> q = new LRUQueue<Integer>();
        for(int i = 0; i < ways; i++){
            q.offer(i);
        }
        LRUQueue<Integer>[] lrus = new LRUQueue[blockAmount];
        for(int i = 0; i < blocksPerWay; i++){
            lrus[i] = q;
        }

        int hits = 0;
        for(String address:addresses){
            Block temp = new Block(address);
            String offset = temp.getData().substring(ADDRESS_SIZE-offsetAmount);
            String index = temp.getData().substring(ADDRESS_SIZE-offsetAmount-indexSize,ADDRESS_SIZE-offsetAmount);
            String tag = temp.getData().substring(0,ADDRESS_SIZE-offsetAmount-indexSize);
            temp.setParams(tag, index, offset);
            int blockIndex = binaryToInt(index);
            int wayIndex = -1;
            //Check sets that are valid first
            for(int way = 0; way < ways; way++){
                if(caches[way][blockIndex].isValid() && caches[way][blockIndex].getTag().equals(tag)){
                    //If matching tag in valid set, offer the tag's way index to the LRUqueue at the current block index (HIT)
                    lrus[blockIndex].offer(way);
                    wayIndex = way;
                    way = ways;
                    hits++;
                }
            }
            //If no matching tags in valid sets, poll the LRUqueue at the current block index for the least recently used value and put it there (MISS)
            if(wayIndex < 0){
                wayIndex = lrus[blockIndex].poll();
            }
            //Once wayIndex is found, set caches[wayIndex][blockIndex] to temp.
            caches[wayIndex][blockIndex] = temp;
        }
        System.out.printf("%d-Way Set Map miss ratio: %d%s\n",ways,(int)(100*((float)(addresses.size()-hits))/addresses.size()),"%");

        return caches;
    }

    public Block[] fullMap(List<String> addresses){
        int blockSize = 32, ways = blockAmount;
        Block[] cache = new Block[ways];
        LRUQueue<Block> q = new LRUQueue<Block>();
        for(int i = 0; i < ways; i++){
                cache[i] = new Block();
                Block queueBlock = new Block();
                queueBlock.setQueueIndex(i);
                q.offer(queueBlock);
        }
        int offsetAmount = (int)(Math.log(blockSize)/Math.log(2));
        int hits = 0;

        for(String address:addresses){
            Block temp = new Block(address);
            String offset = temp.getData().substring(ADDRESS_SIZE-offsetAmount);
            String tag = temp.getData().substring(0,ADDRESS_SIZE-offsetAmount);
            temp.setParams(tag, null, offset);

            int way = -1;
            //Check valid sets
            for(int w = 0; w < ways; w++){
                    if(cache[w].isValid() && cache[w].getTag().equals(tag)){
                        //find proper index in block queue
                        q.offer(q.peekIndex(w));
                                way = w;
                                w = ways;
                                hits++;
                    }
                
            }
            //If no valid set, block, way = LRU
            if(way < 0){
                way = q.poll().getQueueIndex();
            }
            //Once way and block are found, set caches[way][block] to temp.
            cache[way] = temp;
        }
        System.out.printf("Fully Mapped miss ratio: %d%s\n",(int)(100*((float)(addresses.size()-hits))/addresses.size()),"%");

        return cache;
    }

    public void printCache(Block[] cache){
        System.out.println("Index    V                   Data                         Tag");
        for(int i = 0; i < cache.length; i++){
            if(i < 10){
                System.out.print("0");
            }
            System.out.printf("%d      %b    %s %s\n",i,cache[i].isValid(),cache[i].getData(),cache[i].getTag());
        }
    }

    public void printCache(Block[][] caches){
        for(int i = 0; i < caches.length; i++){
            System.out.printf("Way %d:\n",i);
            printCache(caches[i]);
            System.out.println();
        }
    }

    public static int binaryToInt(String str){
        int ret = 0;
        for(int i = str.length()-1; i >= 0; i--){
            if(str.charAt(i) != '0'){
                ret += Math.pow(2,str.length()-i-1);
            }
        }
        return ret;
    }

    public static boolean is2N(double d){
        if(d == 2.0 || d == 1.0){
            return true;
        }
        else if(d < 2 || d%1 != 0){
            return false;
        }
        return is2N(d/2);
    }

    public static String correctAddress(String s){
        while(s.length() < ADDRESS_SIZE){
            s = "0"+s;
        }
        if(s.length() > ADDRESS_SIZE){
            s = s.substring(s.length()-ADDRESS_SIZE);
        }
        return s;
    }

    public static void generateAddresses(int amount){
        //ADDRESS_GENERATION_SIZE can be any number from 1 to ADDRESS_SIZE.
        //If ADDRESS_GENERATION_SIZE is too high, the tag values aren't likely to match.
        //If too low, the tag values are too likely to match, as the tags will all be small (can even become 0).
        //I find for a 32-bit address, 13 or 14 is about the sweet spot, but feel free to change the value if desired.
        
        System.out.println("Generating new addresses...");
        try{
            Random rand = new Random();
            //find/generate file
            File file = new File("addresses.txt");
            if(file.createNewFile()){
                System.out.println("Address file created."); 
            }
            else{
                System.out.println("Existing file found.");
            }
            //write to file
            FileWriter writer = new FileWriter("addresses.txt");
            writer.write("This file contains a list of pre-generated 32-bit addresses. Feel free to randomly generate new ones via the program or insert your own for testing!\n");
            writer.write("You can either have them spaced or on separate lines, and the program will automatically correct addresses with too little or many digits.\n");
            for(int i = 0; i < amount; i++){
                String address = "";
                for(int j = 0; j < ADDRESS_GENERATION_SIZE; j++){
                    address+=rand.nextInt(2);
                }
                writer.write(address+"\n");
            }       
            writer.close();
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    public class Block{
        //Each Cache Line holds a Block.
        //Blocks consist of a "data" String representing a binary number.
        //This String is segmented into tag, index, and offset values.
        private String data, tag, index, offset;
        private int queueIndex;
        private boolean valid;
        public Block(){
            data = "";
            tag = "";
            index = "";
            offset = "";
            valid = false;
        }

        public Block(String data){
            this.data = data;
            tag = "";
            index = "";
            offset = "";
            valid = true;
        }

        public void setParams(String tag, String index, String offset){
            this.tag = tag;
            this.index = index;
            this.offset = offset;
            valid = true;
        }

        public String getData(){
            return data;
        }

        public String getTag(){
            return tag;
        }

        public String getIndex(){
            return index;
        }

        public String getOffset(){
            return offset;
        }

        public boolean isValid(){
            return valid;
        }
        
        public void setQueueIndex(int i){
            queueIndex = i;
        }
        
        public int getQueueIndex(){
            return queueIndex;
        }
    }

    /* LRUQueue is a modified ArrayList with three unique properties:
     * 1: When a value is offered to the LRUqueue, it checks if the value is already in the queue. If it is, it removes it from the queue. The value is then put at the end.
     * 2: When a value is polled from the LRUqueue, it removes the value at index 0 (the least recently used value) and places it at the end (most recently used).
     * 3: The LRUQueue has a trueIndexArray, in which it keeps all of its values in a static order. This allows a method to reliably retrieve any given object in the LRUQueue.
     */
    public class LRUQueue<E> extends ArrayList<E>{
        private Object[] trueIndexArray;
        public LRUQueue(){
            trueIndexArray = new Object[0];
        }

        public E peek(){
            return get(0);
        }

        public void offer(E o){
            if(this.contains(o)){
                remove(o);
            }
            else{
                Object[] newTrueIndexArray = new Object[trueIndexArray.length+1];
                for(int i = 0; i < trueIndexArray.length; i++){
                    newTrueIndexArray[i] = trueIndexArray[i];
                }
                newTrueIndexArray[newTrueIndexArray.length-1] = o;
                trueIndexArray = (E[])newTrueIndexArray;
            }
            add(o);
        }

        public E poll(){
            E temp = remove(0);
            add(temp);
            return temp;
        }
        
        public E peekIndex(int i){
            return (E)trueIndexArray[i];
        }
    }
}