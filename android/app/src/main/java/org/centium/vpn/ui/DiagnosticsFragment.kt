package org.centium.vpn.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.centium.vpn.R
import org.centium.vpn.databinding.FragmentDiagnosticsBinding
import org.centium.vpn.diagnostics.DiagnosticSuite
import org.centium.vpn.vpn.CentiumVpnService

class DiagnosticsFragment : Fragment() {

    private var _binding: FragmentDiagnosticsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnosticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRunAudit.setOnClickListener { runDiagnostics() }
        runDiagnostics()
    }

    private fun runDiagnostics() {
        // Every check inspects the live tunnel, so without one they'd all just fail
        if (!CentiumVpnService.connectionState.value.isConnected) {
            binding.tvAuditSummary.setText(R.string.checks_empty)
            binding.llLayerResults.visibility = View.GONE
            binding.btnRunAudit.visibility = View.GONE
            return
        }

        binding.btnRunAudit.visibility = View.VISIBLE
        binding.btnRunAudit.isEnabled = false
        binding.pbAuditLoading.visibility = View.VISIBLE
        binding.tvAuditSummary.setText(R.string.checks_running)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = DiagnosticSuite(requireContext()).runFullDiagnostics()

            binding.pbAuditLoading.visibility = View.INVISIBLE
            binding.btnRunAudit.isEnabled = true
            binding.tvAuditSummary.text =
                getString(R.string.checks_summary, result.passedCount, result.layerDetails.size)

            val list = binding.llLayerResults
            list.removeAllViews()
            result.layerDetails.forEachIndexed { index, layer ->
                if (index > 0) {
                    list.addView(layoutInflater.inflate(R.layout.item_divider, list, false))
                }
                val row = layoutInflater.inflate(R.layout.item_diagnostic_layer, list, false)
                row.findViewById<View>(R.id.vStatusDot)
                    .setBackgroundResource(if (layer.passed) R.drawable.status_pass else R.drawable.status_fail)
                row.findViewById<TextView>(R.id.tvLayerTitle).text = layer.layerName
                row.findViewById<TextView>(R.id.tvLayerDesc).text = layer.description
                row.findViewById<TextView>(R.id.tvLayerFix).apply {
                    text = layer.remediation
                    visibility = if (!layer.passed && !layer.remediation.isNullOrBlank()) View.VISIBLE else View.GONE
                }
                row.contentDescription = "${layer.layerName}: ${if (layer.passed) "passed" else "failed"}. ${layer.description}"
                list.addView(row)
            }
            list.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
