package org.centium.vpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.centium.vpn.R
import org.centium.vpn.databinding.FragmentLogsBinding
import org.centium.vpn.vpn.CentiumVpnService

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCopyLogs.setOnClickListener {
            val text = CentiumVpnService.logs.value.joinToString("\n")
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.logs_title), text))
            Toast.makeText(requireContext(), R.string.logs_copied, Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            CentiumVpnService.logs.collectLatest { logEntries ->
                binding.btnCopyLogs.isEnabled = logEntries.isNotEmpty()
                if (logEntries.isNotEmpty()) {
                    binding.tvLogOutput.text = logEntries.joinToString("\n")
                    binding.tvLogOutput.setTextColor(requireContext().getColor(R.color.ink))
                    binding.scrollLogs.post {
                        binding.scrollLogs.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
