package quiz

import "math/rand"

// Question is a generated or static multiple-choice networking quiz item.
type Question struct {
	Text        string   `json:"text"`
	Options     []string `json:"options"`
	Correct     int      `json:"correct"`
	Category    string   `json:"category"`
	Explanation string   `json:"explanation"`
}

// staticQuestionPool is a large pool of networking concept questions with explanations.
// The server picks randomly from this pool when the LLM is unavailable or returns bad output.
var staticQuestionPool = []Question{
	// ── CSMA/CD & Medium Access ──
	{
		Text:        "What does CSMA/CD stand for?",
		Options:     []string{"Carrier Sense Multiple Access / Collision Detection", "Carrier Signal Multiple Access / Collision Detection", "Channel Sense Multiple Access / Collision Delay", "Carrier Sense Multi Access / Collision Deferral"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "CSMA/CD stands for Carrier Sense Multiple Access with Collision Detection — stations listen before transmitting and detect collisions when they occur.",
	},
	{
		Text:        "What happens when a collision is detected in CSMA/CD?",
		Options:     []string{"Stations stop and wait a random backoff time", "Stations increase transmission power", "The packet is dropped permanently", "Stations switch to a different channel"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "After detecting a collision, stations send a jam signal then wait a random backoff period before retransmitting, reducing the chance of another collision.",
	},
	{
		Text:        "What is the purpose of exponential backoff in CSMA/CD?",
		Options:     []string{"Reduce repeated collisions by increasing wait time", "Speed up data transmission", "Compress packet headers", "Encrypt network traffic"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Exponential backoff doubles the maximum wait window after each successive collision, spreading out retransmission attempts to reduce congestion.",
	},
	{
		Text:        "Which protocol does WiFi use instead of CSMA/CD?",
		Options:     []string{"CSMA/CA", "TDMA", "FDMA", "Token Ring"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "WiFi uses CSMA/CA (Collision Avoidance) because wireless stations cannot detect collisions during transmission the way wired Ethernet can.",
	},
	{
		Text:        "Why can't wireless networks use collision detection like Ethernet?",
		Options:     []string{"A station cannot transmit and listen simultaneously on the same frequency", "Wireless signals travel too fast", "WiFi uses digital signals instead of analog", "Wireless has unlimited bandwidth"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "In wireless, the transmitted signal is far stronger than any incoming signal at the same antenna, making it impossible to detect collisions during transmission (the 'near-far problem').",
	},
	{
		Text:        "In CSMA/CD, what is a 'jam signal' used for?",
		Options:     []string{"To notify all stations that a collision has occurred", "To request priority access to the channel", "To encrypt the data being transmitted", "To measure the round-trip time"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "After detecting a collision, a station sends a short jam signal so all other stations also detect the collision and stop transmitting.",
	},
	{
		Text:        "What is the maximum number of retransmission attempts in standard CSMA/CD before giving up?",
		Options:     []string{"16", "8", "32", "64"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "The IEEE 802.3 standard specifies a maximum of 16 attempts. After that, the frame is discarded and an error is reported to higher layers.",
	},

	// ── TCP ──
	{
		Text:        "What is an ACK in TCP?",
		Options:     []string{"Acknowledgment that data was received", "A request to resend data", "A connection termination signal", "A keep-alive heartbeat"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "TCP uses ACK (acknowledgment) segments to confirm that data was successfully received, enabling reliable delivery.",
	},
	{
		Text:        "At which OSI layer does TCP operate?",
		Options:     []string{"Transport (Layer 4)", "Network (Layer 3)", "Data Link (Layer 2)", "Application (Layer 7)"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "TCP is a Transport layer (Layer 4) protocol, responsible for end-to-end reliable data delivery between applications.",
	},
	{
		Text:        "Which transport protocol does NOT guarantee delivery?",
		Options:     []string{"UDP", "TCP", "SCTP", "QUIC"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "UDP (User Datagram Protocol) is connectionless and provides no delivery guarantees, retransmission, or ordering — making it faster but unreliable.",
	},
	{
		Text:        "What does TCP's three-way handshake establish?",
		Options:     []string{"A reliable connection between two hosts", "An encrypted tunnel", "A multicast group", "A routing path"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "The SYN → SYN-ACK → ACK handshake synchronises sequence numbers and establishes a reliable, ordered connection between two endpoints.",
	},
	{
		Text:        "What triggers a TCP retransmission?",
		Options:     []string{"The ACK for a sent segment is not received before the timeout", "The receiver sends a FIN", "The network MTU changes", "A DNS lookup fails"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "TCP retransmits a segment when its retransmission timer expires without receiving the corresponding ACK, indicating probable packet loss.",
	},
	{
		Text:        "What is TCP flow control?",
		Options:     []string{"The receiver advertises a window size to prevent the sender from overwhelming it", "The router limits bandwidth on a link", "The application pauses when CPU is busy", "The OS limits the number of open sockets"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "TCP flow control uses the receiver's advertised window size to tell the sender how much data it can accept, preventing buffer overflow.",
	},
	{
		Text:        "What is the difference between TCP and UDP?",
		Options:     []string{"TCP provides reliable ordered delivery; UDP does not", "UDP is encrypted; TCP is not", "TCP is faster than UDP", "UDP requires a handshake; TCP does not"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "TCP guarantees reliable, ordered delivery via acknowledgments and retransmissions. UDP trades reliability for lower latency and overhead.",
	},
	{
		Text:        "What does a TCP FIN segment indicate?",
		Options:     []string{"The sender has finished sending data and wants to close the connection", "A fatal error occurred", "The packet was fragmented", "The connection is being reset"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "FIN (finish) is part of TCP's graceful connection teardown — it signals that the sender has no more data to transmit.",
	},
	{
		Text:        "Why does UDP have lower latency than TCP?",
		Options:     []string{"No connection setup, no acknowledgments, no retransmissions", "UDP compresses data automatically", "UDP uses a faster physical layer", "UDP encrypts headers more efficiently"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "UDP skips the three-way handshake, doesn't wait for ACKs, and never retransmits — eliminating the overhead that makes TCP slower.",
	},

	// ── RTT & Latency ──
	{
		Text:        "What is RTT in networking?",
		Options:     []string{"Round-Trip Time — time for a packet to go and return", "Real-Time Transfer — instant data delivery", "Route Tracking Table — maps network paths", "Retransmission Timeout Trigger"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "RTT (Round-Trip Time) measures the time from sending a packet to receiving its acknowledgment, indicating network latency.",
	},
	{
		Text:        "How does high RTT affect TCP performance?",
		Options:     []string{"Throughput decreases because the sender waits longer for ACKs", "Packets become larger", "The connection is automatically encrypted", "The routing table is recalculated"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "TCP's sliding window means the sender can only have a limited amount of unacknowledged data in flight. Higher RTT means longer waits for ACKs, reducing effective throughput.",
	},
	{
		Text:        "What is jitter in networking?",
		Options:     []string{"Variation in packet arrival times", "Total bandwidth of a link", "Number of hops in a route", "Encryption key rotation interval"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Jitter is the variance in delay between packets. High jitter causes uneven packet delivery, which is especially problematic for real-time applications like VoIP.",
	},

	// ── Mesh & Topology ──
	{
		Text:        "In a star topology, what happens if the central node fails?",
		Options:     []string{"All other nodes lose connectivity", "Only adjacent nodes are affected", "Traffic reroutes automatically", "Nothing — other nodes take over"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "In a star topology, all communication passes through the central hub. If it fails, no node can reach any other node.",
	},
	{
		Text:        "In the Bully Algorithm, which node becomes leader?",
		Options:     []string{"Highest ID", "Lowest ID", "Random node", "First to respond"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "The Bully Algorithm elects the node with the highest ID as leader. Any node with a higher ID can 'bully' a lower-ID node out of the election.",
	},
	{
		Text:        "What triggers a new leader election in the Bully Algorithm?",
		Options:     []string{"A node detects the leader is unreachable", "A timer expires every 60 seconds", "A new node with the lowest ID joins", "The network bandwidth drops below a threshold"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "A Bully election starts when a node notices the current leader is not responding — typically via a missed heartbeat or failed communication.",
	},
	{
		Text:        "What is the advantage of a mesh topology over a star?",
		Options:     []string{"Redundant paths — if one link fails, others provide connectivity", "Lower cost due to fewer connections", "Simpler configuration", "Requires only one central switch"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "A mesh topology has multiple paths between nodes, so a single link failure doesn't disconnect the network — unlike a star where the hub is a single point of failure.",
	},
	{
		Text:        "What does 'peer-to-peer' mean in networking?",
		Options:     []string{"Devices communicate directly without a central server", "All traffic passes through a router", "Only two devices can exist on the network", "Data is always encrypted end-to-end"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "In peer-to-peer (P2P) networking, each device acts as both client and server, communicating directly with other peers without relying on a central server.",
	},
	{
		Text:        "What is a point-to-point topology?",
		Options:     []string{"A direct link between exactly two nodes", "A ring connecting all nodes", "A star with multiple hubs", "A bus with terminators"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Point-to-point is the simplest topology — a dedicated link between two nodes with no sharing of the communication channel.",
	},

	// ── AR & Cloud Anchors ──
	{
		Text:        "What does a Cloud Anchor enable in AR?",
		Options:     []string{"Shared spatial coordinate system across devices", "Storing 3D models in the cloud", "Remote rendering of AR scenes", "GPS-based AR positioning"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Cloud Anchors let multiple devices resolve the same physical location, creating a shared coordinate system so AR content appears in the same place for everyone.",
	},
	{
		Text:        "Why is a shared coordinate system important for multi-user AR?",
		Options:     []string{"So virtual objects appear at the same physical location for all users", "To reduce battery consumption", "To enable faster rendering", "To compress 3D model files"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Without a shared coordinate system, each device's AR world is independent. A shared anchor ensures all users see virtual content in the same physical position.",
	},

	// ── Packet Loss & Reliability ──
	{
		Text:        "What is packet loss?",
		Options:     []string{"When transmitted packets fail to reach their destination", "When packets arrive out of order", "When packets are duplicated", "When packets are encrypted"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Packet loss occurs when one or more transmitted packets fail to arrive at the destination, often due to network congestion, faulty hardware, or signal degradation.",
	},
	{
		Text:        "How does TCP handle packet loss?",
		Options:     []string{"It detects missing ACKs and retransmits the lost segments", "It ignores the loss and continues", "It drops the entire connection", "It switches to UDP automatically"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "TCP uses sequence numbers and acknowledgments to detect lost packets. When an ACK doesn't arrive within the timeout, TCP retransmits the missing segment.",
	},
	{
		Text:        "How does UDP handle packet loss?",
		Options:     []string{"It doesn't — lost packets are simply gone", "It retransmits after a timeout", "It requests the packet from a neighbor", "It sends a NACK to the sender"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "UDP has no built-in reliability mechanism. If a packet is lost, the application must handle it (or accept the loss), which is fine for real-time use cases.",
	},
	{
		Text:        "What effect does 50% packet loss have on TCP throughput?",
		Options:     []string{"Throughput drops dramatically due to constant retransmissions and backoff", "Throughput is exactly halved", "No effect — TCP compensates automatically", "The connection is immediately closed"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "At 50% loss, TCP's congestion control aggressively reduces the sending rate, and frequent retransmissions add overhead — throughput drops far more than 50%.",
	},

	// ── OSI Model & Layers ──
	{
		Text:        "At which OSI layer do IP addresses operate?",
		Options:     []string{"Network (Layer 3)", "Transport (Layer 4)", "Data Link (Layer 2)", "Application (Layer 7)"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "IP addresses are a Network layer (Layer 3) concept, used for logical addressing and routing packets between networks.",
	},
	{
		Text:        "What does the Data Link layer (Layer 2) handle?",
		Options:     []string{"Framing, MAC addressing, and error detection on a single link", "End-to-end encryption", "DNS resolution", "Application-level data formatting"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Layer 2 handles communication on a single physical link — it frames data, uses MAC addresses for local delivery, and detects transmission errors.",
	},
	{
		Text:        "Which OSI layer is responsible for routing?",
		Options:     []string{"Network (Layer 3)", "Transport (Layer 4)", "Physical (Layer 1)", "Session (Layer 5)"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "The Network layer (Layer 3) handles routing — determining the path packets take from source to destination across multiple networks.",
	},

	// ── General Networking ──
	{
		Text:        "What is bandwidth in networking?",
		Options:     []string{"The maximum rate of data transfer across a network path", "The physical length of a cable", "The number of devices on a network", "The encryption strength of a connection"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Bandwidth is the maximum data throughput of a network link, typically measured in bits per second (bps). Higher bandwidth means more data can flow per unit time.",
	},
	{
		Text:        "What is the difference between latency and bandwidth?",
		Options:     []string{"Latency is delay per packet; bandwidth is data rate capacity", "They are the same thing", "Latency only affects UDP; bandwidth only affects TCP", "Bandwidth causes latency"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Latency measures the time delay for a single packet to travel from source to destination. Bandwidth measures how much data the link can carry per second. Both affect performance independently.",
	},
	{
		Text:        "What is a broadcast in networking?",
		Options:     []string{"Sending a message to all devices on the network", "Sending a message to one specific device", "Encrypting a message for secure delivery", "Compressing data for faster transfer"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "A broadcast sends a single packet that is delivered to all devices on the local network segment, useful for discovery protocols but inefficient at scale.",
	},
	{
		Text:        "What is network congestion?",
		Options:     []string{"When traffic exceeds the network's capacity, causing delays and packet loss", "When a cable is physically damaged", "When encryption slows down data transfer", "When too few devices are connected"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Congestion occurs when the volume of traffic exceeds what the network can handle, leading to queuing delays, increased latency, and packet drops.",
	},

	// ── Congestion Control ──
	{
		Text:        "What is TCP congestion control?",
		Options:     []string{"A mechanism to reduce sending rate when network congestion is detected", "A firewall feature that blocks excess traffic", "A DNS load-balancing strategy", "An encryption protocol for busy networks"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "TCP congestion control (e.g. slow start, congestion avoidance) dynamically adjusts the sender's rate based on network feedback like packet loss and delay.",
	},
	{
		Text:        "What is the TCP 'slow start' phase?",
		Options:     []string{"The sender begins with a small window and doubles it each RTT until loss occurs", "The sender waits 10 seconds before transmitting", "The receiver slows down ACKs", "The router queues packets for later delivery"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Slow start exponentially increases the congestion window (doubling per RTT) to quickly probe available capacity, then switches to linear increase after a threshold.",
	},
	{
		Text:        "What is a TCP sequence number used for?",
		Options:     []string{"To identify the position of data bytes in the stream for ordering and reassembly", "To count the number of connections", "To encrypt the payload", "To measure RTT"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Sequence numbers let the receiver reassemble data in the correct order and detect missing segments, even if packets arrive out of order.",
	},

	// ── Discovery & P2P ──
	{
		Text:        "What is service discovery in peer-to-peer networking?",
		Options:     []string{"The process of finding available peers or services on the network", "Encrypting all service communications", "Assigning static IP addresses", "Compressing service advertisements"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Service discovery (e.g. mDNS, Nearby Connections advertising) lets devices announce and find services on the local network without a central directory.",
	},
	{
		Text:        "What is the P2P_CLUSTER strategy in Nearby Connections?",
		Options:     []string{"All devices can both advertise and discover simultaneously", "Devices form a strict client-server hierarchy", "Only one device can advertise at a time", "Devices connect through a cloud relay"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "P2P_CLUSTER allows every device to advertise and discover at the same time, enabling flexible ad-hoc mesh formation without predetermined roles.",
	},
	{
		Text:        "What is NAT traversal?",
		Options:     []string{"Techniques to establish direct connections between devices behind NAT routers", "A method to increase WiFi range", "A protocol for translating domain names", "Encryption of router configuration"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "NAT traversal (e.g. STUN, TURN, hole punching) allows P2P connections even when devices are behind NAT, which normally blocks unsolicited inbound traffic.",
	},

	// ── Error Detection ──
	{
		Text:        "What is a checksum used for in networking?",
		Options:     []string{"To detect errors in transmitted data", "To encrypt the payload", "To compress the header", "To route the packet"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "A checksum is a computed value sent with data that the receiver recalculates to verify the data wasn't corrupted during transmission.",
	},
	{
		Text:        "What is the difference between error detection and error correction?",
		Options:     []string{"Detection identifies corrupted data; correction can fix it without retransmission", "They are the same thing", "Detection is for TCP; correction is for UDP", "Detection works at Layer 1; correction at Layer 7"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Error detection (e.g. checksums, CRC) only identifies that corruption occurred. Error correction (e.g. FEC, Hamming codes) includes enough redundancy to reconstruct the original data.",
	},

	// ── Fragmentation & MTU ──
	{
		Text:        "What is MTU in networking?",
		Options:     []string{"Maximum Transmission Unit — the largest packet size a link can carry", "Minimum Transfer Utility", "Maximum Timeout Upstream", "Multi-Threaded Upload speed"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "MTU defines the maximum packet size (in bytes) that a network link can transmit in a single frame. Packets larger than the MTU must be fragmented.",
	},

	// ── Multicast & Anycast ──
	{
		Text:        "What is multicast?",
		Options:     []string{"Sending a packet to a specific group of interested receivers", "Sending a packet to all devices on the network", "Sending a packet to exactly one device", "Encrypting a packet for group delivery"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Multicast delivers a single packet to all members of a multicast group, more efficient than sending individual copies to each receiver (unicast) or all devices (broadcast).",
	},

	// ── ARP & MAC ──
	{
		Text:        "What does ARP do?",
		Options:     []string{"Resolves an IP address to a MAC address on the local network", "Encrypts Layer 2 frames", "Assigns IP addresses dynamically", "Routes packets between networks"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "ARP (Address Resolution Protocol) maps a known IP address to the corresponding MAC address so frames can be delivered on the local link.",
	},
	{
		Text:        "What is a MAC address?",
		Options:     []string{"A hardware address uniquely identifying a network interface", "A software-assigned IP address", "A routing table entry", "An encryption key for WiFi"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "A MAC (Media Access Control) address is a 48-bit hardware identifier burned into a network interface card, used for Layer 2 frame delivery.",
	},

	// ── Leader Election Variations ──
	{
		Text:        "What is the Ring Election algorithm?",
		Options:     []string{"Nodes pass election messages around a logical ring until the highest ID is found", "Nodes form a physical ring topology", "The token holder becomes leader", "Nodes vote by broadcast"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "In the Ring algorithm, election messages travel around a logical ring. Each node forwards the message with the highest ID it knows until the message returns to its originator.",
	},
	{
		Text:        "Why is leader election important in distributed systems?",
		Options:     []string{"To coordinate actions that require a single decision-maker", "To encrypt all communications", "To reduce bandwidth usage", "To increase the number of connections"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "A leader provides a single coordination point for tasks like anchor hosting, message routing, and conflict resolution — avoiding split-brain scenarios.",
	},

	// ── Reliability & QoS ──
	{
		Text:        "What is Quality of Service (QoS)?",
		Options:     []string{"Mechanisms to prioritize certain traffic types for better performance", "A measure of WiFi signal strength", "The total number of connected devices", "An encryption standard"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "QoS lets network equipment prioritize latency-sensitive traffic (e.g. VoIP, gaming) over bulk transfers, ensuring critical applications get adequate bandwidth and low delay.",
	},
	{
		Text:        "What is a retransmission timeout (RTO) in TCP?",
		Options:     []string{"The time TCP waits before assuming a packet was lost and resending it", "The maximum connection duration", "The time between DNS queries", "The delay before establishing a new connection"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "RTO is dynamically calculated from RTT measurements. If an ACK doesn't arrive within the RTO, TCP retransmits the segment.",
	},

	// ── Consensus & Split Brain ──
	{
		Text:        "What is 'split brain' in a distributed system?",
		Options:     []string{"Two or more nodes each believe they are the leader simultaneously", "A network cable is physically split", "The database is partitioned into shards", "A node runs out of memory"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Split brain occurs when a network partition or stale messages cause multiple nodes to think they're the leader. Term numbers and quorum-based consensus prevent this.",
	},
	{
		Text:        "How do term numbers prevent split brain in leader election?",
		Options:     []string{"Each election increments a term counter; stale COORDINATOR messages with old terms are rejected", "Terms encrypt the leader's identity", "Terms limit how long a leader can serve", "Terms count the number of connected peers"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Term numbers create a monotonically increasing epoch. A COORDINATOR from term 2 is rejected if the receiver is already in term 3, preventing an old leader from reclaiming leadership.",
	},
	{
		Text:        "What is consensus in a distributed system?",
		Options:     []string{"All nodes agreeing on the same value or decision despite failures", "All nodes having identical hardware", "Nodes sharing the same IP address", "A voting system for user preferences"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Consensus means all non-faulty nodes agree on a single value. Leader election is a form of consensus — all peers agree on who the coordinator is.",
	},
	{
		Text:        "What is state replication in distributed systems?",
		Options:     []string{"The leader copies its state to followers so all nodes have consistent data", "Duplicating the entire server hardware", "Creating backup DNS entries", "Encrypting state before transmission"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "State replication ensures all nodes share the same configuration or data. In a mesh network, the leader replicates settings (drop rates, timeouts) to followers for consistent behavior.",
	},
	{
		Text:        "What is a failure detector in distributed systems?",
		Options:     []string{"A mechanism to detect when a node has stopped responding, usually via timeouts", "A hardware diagnostic tool", "A firewall rule that blocks failed packets", "An encryption algorithm that detects tampering"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Failure detectors use timeouts to suspect unresponsive nodes. If no heartbeat or expected message arrives within a deadline, the node is presumed failed and recovery starts.",
	},
	{
		Text:        "What is causal ordering in distributed messaging?",
		Options:     []string{"Ensuring that if event A caused event B, all nodes see A before B", "Sorting messages alphabetically", "Encrypting messages in order", "Sending messages at fixed intervals"},
		Correct:     0,
		Category:    "CONCEPT",
		Explanation: "Causal ordering preserves the happened-before relationship: if sending a message caused a reply, all observers see the original before the reply. Sequence numbers and vector clocks enable this.",
	},
}

// pickStaticQuestions returns n randomly selected questions from the static pool.
// Options within each question are shuffled so the correct answer isn't always index 0.
func PickStaticQuestions(n int) []Question {
	pool := make([]Question, len(staticQuestionPool))
	copy(pool, staticQuestionPool)
	for i := range pool {
		opts := make([]string, len(pool[i].Options))
		copy(opts, pool[i].Options)
		pool[i].Options = opts
	}
	rand.Shuffle(len(pool), func(i, j int) { pool[i], pool[j] = pool[j], pool[i] })

	if n > len(pool) {
		n = len(pool)
	}
	picked := pool[:n]

	// Shuffle options so correct isn't always at index 0
	for i := range picked {
		q := &picked[i]
		correctText := q.Options[q.Correct]
		rand.Shuffle(len(q.Options), func(a, b int) { q.Options[a], q.Options[b] = q.Options[b], q.Options[a] })
		for j, opt := range q.Options {
			if opt == correctText {
				q.Correct = j
				break
			}
		}
	}
	return picked
}
