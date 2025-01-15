# MIbay Project

The **MIbay Project** is a Java-based auctioning system for selling and buying virtual goods (text files) within a local network. It consists of two components:

1. **MIbayCLI**: A Command Line Interface for users to interact with their agent.
2. **MIbayAgent**: An agent program that communicates with other agents in the network to facilitate auctions and file transfers.

---

## Features

### MIbayCLI
The MIbayCLI program allows users to send commands to their agent. It is started with:
```bash
java MIbayCLI.java <command> [parameters]
```
#### Supported Commands:
1. **anbieten**: Offer a file for auction.
   ```bash
   java MIbayCLI.java anbieten <minimum_bid> <duration_seconds> <file_name>
   ```
   Example:
   ```bash
   java MIbayCLI.java anbieten 20 300 sample.txt
   ```
   Offers `sample.txt` for auction at a minimum bid of 20 for 300 seconds.

2. **bieten**: Place a bid on an auctioned file.
   ```bash
   java MIbayCLI.java bieten <bid_amount> <seller_username> <file_name>
   ```
   Example:
   ```bash
   java MIbayCLI.java bieten 25 jbiff007 sample.txt
   ```
   Places a bid of 25 on `sample.txt` offered by user `jbiff007`.

3. **abbrechen**: Cancel your own ongoing auction.
   ```bash
   java MIbayCLI.java abbrechen <file_name>
   ```
   Example:
   ```bash
   java MIbayCLI.java abbrechen sample.txt
   ```

4. **liste**: List all ongoing auctions in the network.
   ```bash
   java MIbayCLI.java liste
   ```

5. **info**: Display account and auction information.
   ```bash
   java MIbayCLI.java info
   ```
   Output includes:
   - Current balance.
   - Active bids.
   - Total funds locked in bids.

---

### MIbayAgent
The MIbayAgent handles communication, auction management, and file transfers. It runs persistently and interacts with other agents in the network.

#### How to Start
Run the agent with a starting balance:
```bash
java MIbayAgent.java <starting_balance>
```
Example:
```bash
java MIbayAgent.java 1000
```
This starts the agent with a balance of 1000 units.

#### Key Responsibilities:
1. **Auction Management**:
   - Tracks active auctions.
   - Handles incoming bids and ensures only valid bids are accepted.

2. **File Transfer**:
   - Transfers auctioned files to the highest bidder after the auction ends.
   - Saves files in the `dateien` folder of the respective agent.

3. **Balance Management**:
   - Adjusts balances of buyers and sellers after successful auctions.

4. **Communication**:
   - Uses Java UDP sockets for communication between agents.

---

## Folder Structure
```
MIbayProject/
├── MIbayCLI.java      # Command-line interface program
├── MIbayAgent.java    # Agent program
├── dateien/           # Folder to store auctioned files
```

---

## Example Workflow
1. Start the agent for two users:
   ```bash
   java MIbayAgent.java 1000
   java MIbayAgent.java 800
   ```

2. User 1 offers a file for auction:
   ```bash
   java MIbayCLI anbieten 50 300 document.txt
   ```

3. User 2 places a bid:
   ```bash
   java MIbayCLI.java bieten 60 user1 document.txt
   ```

4. After 300 seconds, the auction ends, and the file is transferred to the highest bidder.

5. User 1’s balance increases, and User 2’s balance decreases by the bid amount.

---

## Notes
- **File Size Limit**: Files up to 1 MB can be auctioned.
- **Local Network**: Agents must be in the same LAN for communication.
- **Java Sockets**: Communication is implemented using Java UDP sockets.

---

## License
This project is licensed under the MIT License.

