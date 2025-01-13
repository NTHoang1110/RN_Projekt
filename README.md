# MIbayAgent

MIbayAgent is a Java-based auctioning system that facilitates file-based auctions within a network. It uses UDP sockets for communication and supports features such as listing auctions, placing bids, and sending files.

## Features
- **Create Auctions:** Users can offer files for auction by specifying a starting price, auction duration, and file name.
- **Place Bids:** Users can place bids on active auctions.
- **Cancel Auctions:** Sellers can cancel their auctions.
- **List Auctions:** Displays all active auctions with relevant details.
- **File Transfer:** Sends and receives files between users during the auction process.

## Project Structure
The project consists of several key components:

### Classes and Threads
1. **Main Class:**
   - Initializes the program and starts the required listener threads.
2. **Threads:**
   - `CLIListener`: Handles user commands from the command line interface.
   - `RequestListener`: Listens for incoming messages and file transfer requests.
3. **Auction Class:**
   - Represents an auction with properties such as file name, starting price, expiry time, seller, and highest bid.
4. **Bid Class:**
   - Represents a bid with properties such as bidder name, bid amount, and file name.

### Networking
- **UDP Communication:**
  - Used for broadcasting messages, such as auction announcements and bids.
  - Facilitates file transfers using the `FileSender` and `FileReceiver` programs.

### File Management
- Files are stored in specific directories (`../dateien`) during the auction process.
- Supports appending and creating files dynamically.

## Installation
1. Clone the repository or copy the source files to your local system.
2. Ensure you have the Java Development Kit (JDK) installed (version 8 or above).
3. Place the required files for auctions in the `sister` directory (relative to the program).
4. Compile the program using:
   ```bash
   javac MIbayAgent.java
   ```

## Usage
1. Run the program:
   ```bash
   java MIbayAgent <starting_balance>
   ```
2. Use the following commands:
   - **Create Auction:**
     ```bash
     anbieten <start_price> <time_in_seconds> <file_name>
     ```
   - **Place Bid:**
     ```bash
     bieten <bid_amount> <seller_name> <file_name>
     ```
   - **Cancel Auction:**
     ```bash
     abbrechen <file_name>
     ```
   - **List Auctions:**
     ```bash
     liste
     ```
   - **Get Auction Info:**
     ```bash
     info
     ```

## Example
1. Start the agent:
   ```bash
   java MIbayAgent 1000
   ```
2. Offer a file for auction:
   ```bash
   anbieten 50 120 file.txt
   ```
3. Place a bid:
   ```bash
   bieten 75 seller123 file.txt
   ```
4. Cancel an auction:
   ```bash
   abbrechen file.txt
   ```
5. List all active auctions:
   ```bash
   liste
   ```

## Contributing
1. Fork the repository.
2. Create a new branch:
   ```bash
   git checkout -b feature-name
   ```
3. Make your changes and commit them:
   ```bash
   git commit -m "Description of changes"
   ```
4. Push to the branch:
   ```bash
   git push origin feature-name
   ```
5. Open a pull request.

## License
This project is licensed under the MIT License.

