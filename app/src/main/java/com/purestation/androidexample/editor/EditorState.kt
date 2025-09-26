package com.purestation.androidexample.editor

data class EditorState(
    val blocks: List<Block>
) {
    fun deepCopy(): EditorState {
        val list = blocks.map { b ->
            when (b) {
                is Block.Paragraph -> Block.Paragraph(b.text.deepCopy(), b.isH1)
                is Block.ImageGrid -> Block.ImageGrid(b.uris.toMutableList(), b.columns)
                is Block.Video -> Block.Video(b.uri)
                is Block.YouTube -> Block.YouTube(b.videoId)
            }
        }
        return EditorState(list)
    }
}

class UndoRedo {
    private val undo = ArrayDeque<EditorState>()
    private val redo = ArrayDeque<EditorState>()

    fun push(state: EditorState) {
        undo.addLast(state.deepCopy())
        if (undo.size > 50) undo.removeFirst()
        redo.clear()
    }

    fun canUndo() = undo.isNotEmpty()
    fun canRedo() = redo.isNotEmpty()

    fun popUndo(current: EditorState): EditorState? {
        if (undo.isEmpty()) return null
        val prev = undo.removeLast()
        redo.addLast(current.deepCopy())
        return prev.deepCopy()
    }

    fun popRedo(current: EditorState): EditorState? {
        if (redo.isEmpty()) return null
        val next = redo.removeLast()
        undo.addLast(current.deepCopy())
        return next.deepCopy()
    }

    fun clear() {
        undo.clear(); redo.clear()
    }
}
