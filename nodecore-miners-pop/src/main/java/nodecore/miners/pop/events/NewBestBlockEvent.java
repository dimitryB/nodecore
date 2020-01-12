// VeriBlock PoP Miner
// Copyright 2017-2018 VeriBlock, Inc.
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package nodecore.miners.pop.events;

import org.bitcoinj.core.StoredBlock;

public class NewBestBlockEvent {

    private final StoredBlock block;

    public StoredBlock getBlock() {
        return block;
    }

    public NewBestBlockEvent(StoredBlock block) {
        this.block = block;
    }
}
