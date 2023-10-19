import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        if (tx == null || tx.getInputs() == null || tx.getOutputs() == null || tx.getHash() == null) return false;

        Set<UTXO> doubleSpentSet = new HashSet<>();
        double inputValues = 0.0;
        for (int i = 0; i < tx.numInputs(); i ++) {
            Transaction.Input in = tx.getInput(i);

            // check if input originates from UTXOPool and if double spent and if input siganture is valid
            if (in == null || in.prevTxHash == null || in.outputIndex < 0 || in.signature == null) return false; 

            UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);

            if (!utxoPool.contains(utxo) || !doubleSpentSet.add(utxo)) return false;

            // check if input siganture is valid and if output value is valid, pubKey is the output address
            Transaction.Output op = utxoPool.getTxOutput(utxo);

            if (op == null || op.address == null || op.value <= 0.0) return false;

            // the address is the input owner's address, so check for current tx input-output pair is correct
            byte[] message = tx.getRawDataToSign(i);
            if (message == null || !Crypto.verifySignature(op.address, message, in.signature)) return false;

            inputValues += op.value;
        }

        // check if inputs larger than outputs
        double outputValues = 0.0;
        for (Transaction.Output op : tx.getOutputs()) {
            if (op.address == null || op.value <= 0.0) return false;
            outputValues += op.value;
        } 

        if (inputValues < outputValues) return false;

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        Set<Transaction> validTxs = new HashSet<>();

        for (Transaction tx : possibleTxs) {
            // if tx is valid
            if (isValidTx(tx)) {
                // remove used utxos in pool
                for (Transaction.Input in : tx.getInputs()) {
                    UTXO utxo = new UTXO(in.prevTxHash, in.outputIndex);
                    utxoPool.removeUTXO(utxo);
                }

                // add new utxos into pool
                for (int i = 0; i < tx.numOutputs(); i ++) {
                    Transaction.Output op = tx.getOutput(i);
                    UTXO utxo = new UTXO(tx.getHash(), i);
                    utxoPool.addUTXO(utxo, op);
                }

                validTxs.add(tx);
            }
        }

        return (Transaction[]) validTxs.toArray(new Transaction[validTxs.size()]);
    }

}
