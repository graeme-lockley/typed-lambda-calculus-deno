package tlca.bci

enum class InstructionOpCode {
    PUSH_BUILTIN,
    PUSH_CLOSURE,
    PUSH_DATA,
    PUSH_DATA_ITEM,
    PUSH_FALSE,
    PUSH_INT,
    PUSH_STRING,
    PUSH_TRUE,
    PUSH_TUPLE,
    PUSH_TUPLE_ITEM,
    PUSH_UNIT,
    PUSH_VAR,
    DUP,
    DISCARD,
    SWAP,
    ADD,
    SUB,
    MUL,
    DIV,
    EQ,
    JMP,
    JMP_DATA,
    JMP_FALSE,
    JMP_TRUE,
    SWAP_CALL,
    ENTER,
    RET,
    STORE_VAR
}